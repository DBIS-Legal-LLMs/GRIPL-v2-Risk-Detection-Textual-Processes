package de.mertendieckmann.griplbackend.evaluation.text

import de.mertendieckmann.griplbackend.model.dto.TextLabel

/**
 * Scoring of the text modality: compares the activities extracted and classified by the text analysis with the
 * labeled passages ([TextLabel]) of a test case. Text has no stable element ids like BPMN, so predictions and labels
 * are compared by their position in the process text, in four steps:
 *
 * 1. **Locate** – the quoted passage ([PredictedActivity.exactText]) of every prediction is searched in the text.
 * 2. **Match** – predicted and labeled spans are paired one-to-one by overlap ([MatchMode]).
 * 3. **Classify** – every label is a unit of the confusion matrix (like a BPMN element): TP/FN for critical labels,
 *    FP/TN for non-critical ones. Critical predictions without a matched label are additional FPs.
 * 4. **Extraction metrics** – how well step 1 of the text pipeline found the labeled activities.
 *
 * All functions are pure, so the procedure can be tested in isolation.
 */

/** Character span in the process text, [start] inclusive, [end] exclusive. */
data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "Invalid span [$start, $end)" }
    }

    val length: Int get() = end - start

    fun overlap(other: TextSpan): Int = (minOf(end, other.end) - maxOf(start, other.start)).coerceAtLeast(0)

    /** Intersection over union, 0 for disjoint spans. */
    fun iou(other: TextSpan): Double {
        val intersection = overlap(other)
        if (intersection == 0) return 0.0
        return intersection.toDouble() / (maxOf(end, other.end) - minOf(start, other.start))
    }
}

/** An activity extracted by the text analysis; [isCritical] when it was classified as GDPR critical. */
data class PredictedActivity(
    val id: String,
    val exactText: String,
    val isCritical: Boolean
)

/** A prediction with its position in the text, or `span == null` if its passage could not be found. */
data class LocatedActivity(
    val activity: PredictedActivity,
    val span: TextSpan?
)

enum class MatchMode {
    /** Any character overlap pairs a prediction with a label — robust against different span granularity. */
    PARTIAL,

    /** Only pairs with an intersection over union of at least [STRICT_MIN_IOU] are matched. */
    STRICT;

    companion object {
        const val STRICT_MIN_IOU = 0.5
    }
}

data class SpanMatch(
    val labelId: String,
    val predictionId: String,
    val overlap: Int,
    val iou: Double
)

/** Why a critical label ended up as false negative: not extracted at all, or extracted but classified non-critical. */
enum class FalseNegativeCause { EXTRACTION, CLASSIFICATION }

data class TextScoringResult(
    val mode: MatchMode,
    val located: List<LocatedActivity>,
    val matches: List<SpanMatch>,
    val truePositiveLabelIds: List<String>,
    val falseNegativeLabelIds: Map<String, FalseNegativeCause>,
    /** Non-critical labels whose matched prediction was classified critical. */
    val falsePositiveLabelIds: List<String>,
    /** Critical predictions that are not matched to any label (not locatable or outside all labels). */
    val unmatchedCriticalPredictionIds: List<String>,
    val trueNegativeLabelIds: List<String>,
    /** Labels that were skipped because their offsets no longer point at their passage (orphaned labels). */
    val ignoredLabelIds: List<String>,
    val labelCount: Int
) {
    val truePositives: Int get() = truePositiveLabelIds.size
    val falsePositives: Int get() = falsePositiveLabelIds.size + unmatchedCriticalPredictionIds.size
    val falseNegatives: Int get() = falseNegativeLabelIds.size
    val trueNegatives: Int get() = trueNegativeLabelIds.size
    val falseNegativesByExtraction: Int get() = falseNegativeLabelIds.count { it.value == FalseNegativeCause.EXTRACTION }
    val falseNegativesByClassification: Int get() = falseNegativeLabelIds.count { it.value == FalseNegativeCause.CLASSIFICATION }

    val predictedCount: Int get() = located.size
    val localizedCount: Int get() = located.count { it.span != null }
    val matchedCount: Int get() = matches.size

    /** Share of the extracted activities that correspond to a labeled activity. */
    val extractionPrecision: Double get() = ratio(matchedCount, predictedCount)

    /** Share of the labeled activities that were extracted. */
    val extractionRecall: Double get() = ratio(matchedCount, labelCount)

    val extractionF1: Double
        get() = if (extractionPrecision + extractionRecall > 0) {
            2 * extractionPrecision * extractionRecall / (extractionPrecision + extractionRecall)
        } else 0.0

    /** Share of the quoted passages that exist in the text — how faithful the LLM quotes its source. */
    val localizationRate: Double get() = ratio(localizedCount, predictedCount)

    /** Mean intersection over union of all matches — how well the passage boundaries agree. */
    val meanIou: Double get() = if (matches.isEmpty()) 0.0 else matches.sumOf { it.iou } / matches.size

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator > 0) numerator.toDouble() / denominator else 0.0
}

/**
 * Finds [passage] in [text]: exact match first, then a lenient match that ignores case and whitespace differences
 * (LLM quotes sometimes deviate slightly). Occurrences in [occupied] are skipped, so a passage quoted twice maps to
 * two different occurrences. Returns null if the passage cannot be found.
 */
fun locatePassage(text: String, passage: String, occupied: Set<TextSpan> = emptySet()): TextSpan? {
    val trimmed = passage.trim()
    if (trimmed.isEmpty()) return null

    var index = text.indexOf(trimmed)
    while (index != -1) {
        val span = TextSpan(index, index + trimmed.length)
        if (span !in occupied) return span
        index = text.indexOf(trimmed, index + 1)
    }

    val pattern = trimmed.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
    return Regex(pattern, RegexOption.IGNORE_CASE).findAll(text)
        .map { TextSpan(it.range.first, it.range.last + 1) }
        .firstOrNull { it !in occupied }
}

/** Step 1: locates the passages of all predictions, in order. */
fun locateActivities(text: String, predictions: List<PredictedActivity>): List<LocatedActivity> {
    val occupied = mutableSetOf<TextSpan>()
    return predictions.map { prediction ->
        val span = locatePassage(text, prediction.exactText, occupied)
        span?.let { occupied += it }
        LocatedActivity(prediction, span)
    }
}

/**
 * Step 2: pairs labels and located predictions one-to-one. All overlapping pairs (in [MatchMode.STRICT] only those
 * with IoU >= 0.5) are sorted by overlap, then IoU, and assigned greedily while both sides are still free. Remaining
 * ties are broken by text position, so the result is deterministic.
 */
fun matchSpans(labels: List<TextLabel>, located: List<LocatedActivity>, mode: MatchMode): List<SpanMatch> {
    data class Candidate(val label: TextLabel, val prediction: LocatedActivity, val span: TextSpan, val overlap: Int, val iou: Double)

    val candidates = labels.flatMap { label ->
        val labelSpan = TextSpan(label.start, label.end)
        located.mapNotNull { prediction ->
            val span = prediction.span ?: return@mapNotNull null
            val overlap = labelSpan.overlap(span)
            val iou = labelSpan.iou(span)
            val accepted = overlap > 0 && (mode == MatchMode.PARTIAL || iou >= MatchMode.STRICT_MIN_IOU)
            if (accepted) Candidate(label, prediction, span, overlap, iou) else null
        }
    }.sortedWith(
        compareByDescending<Candidate> { it.overlap }
            .thenByDescending { it.iou }
            .thenBy { it.label.start }
            .thenBy { it.span.start }
    )

    val usedLabels = mutableSetOf<String>()
    val usedPredictions = mutableSetOf<String>()
    val matches = mutableListOf<SpanMatch>()
    for (candidate in candidates) {
        val labelId = candidate.label.id
        val predictionId = candidate.prediction.activity.id
        if (labelId in usedLabels || predictionId in usedPredictions) continue
        usedLabels += labelId
        usedPredictions += predictionId
        matches += SpanMatch(labelId, predictionId, candidate.overlap, candidate.iou)
    }
    return matches
}

/** A label is usable while its offsets lie in the text and still point at its passage. */
fun isLabelAnchored(text: String, label: TextLabel): Boolean =
    label.start in 0..label.end && label.end <= text.length && text.substring(label.start, label.end) == label.text

/** Scores one test case of the text modality. */
fun scoreText(
    processText: String,
    labels: List<TextLabel>,
    predictions: List<PredictedActivity>,
    mode: MatchMode = MatchMode.PARTIAL
): TextScoringResult {
    val (anchoredLabels, orphanedLabels) = labels.partition { isLabelAnchored(processText, it) }
    val located = locateActivities(processText, predictions)
    val matches = matchSpans(anchoredLabels, located, mode)

    val predictionByLabelId = matches.associate { match ->
        match.labelId to located.first { it.activity.id == match.predictionId }.activity
    }
    val matchedPredictionIds = matches.map { it.predictionId }.toSet()

    val truePositives = mutableListOf<String>()
    val falseNegatives = linkedMapOf<String, FalseNegativeCause>()
    val falsePositives = mutableListOf<String>()
    val trueNegatives = mutableListOf<String>()
    for (label in anchoredLabels.sortedBy { it.start }) {
        val prediction = predictionByLabelId[label.id]
        val predictedCritical = prediction?.isCritical == true
        when {
            label.isCritical && predictedCritical -> truePositives += label.id
            label.isCritical -> falseNegatives[label.id] =
                if (prediction == null) FalseNegativeCause.EXTRACTION else FalseNegativeCause.CLASSIFICATION
            predictedCritical -> falsePositives += label.id
            else -> trueNegatives += label.id
        }
    }

    return TextScoringResult(
        mode = mode,
        located = located,
        matches = matches,
        truePositiveLabelIds = truePositives,
        falseNegativeLabelIds = falseNegatives,
        falsePositiveLabelIds = falsePositives,
        unmatchedCriticalPredictionIds = located
            .filter { it.activity.isCritical && it.activity.id !in matchedPredictionIds }
            .map { it.activity.id },
        trueNegativeLabelIds = trueNegatives,
        ignoredLabelIds = orphanedLabels.map { it.id },
        labelCount = anchoredLabels.size
    )
}
