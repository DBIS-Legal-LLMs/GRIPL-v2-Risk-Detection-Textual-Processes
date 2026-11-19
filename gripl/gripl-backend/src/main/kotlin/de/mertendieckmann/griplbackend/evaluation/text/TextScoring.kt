package de.mertendieckmann.griplbackend.evaluation.text

import de.mertendieckmann.griplbackend.model.dto.TextLabel
import de.mertendieckmann.griplbackend.model.dto.TextLabelKind

/**
 * Scoring of the text modality: compares the activities extracted and classified by the text analysis with the
 * labeled passages ([TextLabel]) of a test case. Text has no stable element ids like BPMN, so predictions and labels
 * are compared by their position in the process text, in four steps:
 *
 * 1. **Locate** – the quoted passage ([PredictedActivity.exactText]) of every prediction is searched in the text,
 *    tolerating small deviations of the quote ([locatePassage]).
 * 2. **Match** – predicted and labeled spans are paired one-to-one by overlap ([MatchMode]).
 * 3. **Classify** – every activity label is a unit of the confusion matrix (like a BPMN element): TP/FN for critical
 *    labels, FP/TN for non-critical ones. Critical predictions without a matched label are additional FPs, unless they
 *    overlap a neutral label ([TextLabelKind.NEUTRAL]): those are not scored.
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
    /** Unmatched predictions inside a neutral label: not scored, excluded from the extraction precision. */
    val neutralPredictionIds: List<String>,
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
    val neutralCount: Int get() = neutralPredictionIds.size

    /** Critical predictions inside neutral labels — the false positives the neutral labels avoid. */
    val neutralCriticalCount: Int
        get() = located.count { it.activity.isCritical && it.activity.id in neutralPredictionIds }
    val localizedCount: Int get() = located.count { it.span != null }
    val matchedCount: Int get() = matches.size

    /** Share of the extracted activities (except those in neutral labels) that correspond to a labeled activity. */
    val extractionPrecision: Double get() = ratio(matchedCount, predictedCount - neutralCount)

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
 * Finds [passage] in [text], in increasingly lenient stages, because LLM quotes often deviate slightly from their
 * source:
 * 1. exact match,
 * 2. ignoring case and whitespace differences,
 * 3. additionally ignoring punctuation at the edges of the passage (e.g. an added final period),
 * 4. word-based ([locateByWords]): the text window whose words agree best with the passage — catches reordered
 *    quotes (German word order "überarbeitet sie die Daten" quoted as "sie überarbeitet die Daten"), an added subject
 *    or an inflected verb, but not paraphrases.
 *
 * Occurrences in [occupied] are skipped, so a passage quoted twice maps to two different occurrences. Returns null if
 * the passage cannot be found.
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

    val withoutEdgePunctuation = trimmed.trim { !it.isLetterOrDigit() }
    return sequenceOf(trimmed, withoutEdgePunctuation)
        .filter { it.isNotEmpty() }
        .firstNotNullOfOrNull { candidate ->
            val pattern = candidate.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE).findAll(text)
                .map { TextSpan(it.range.first, it.range.last + 1) }
                .firstOrNull { it !in occupied }
        }
        ?: locateByWords(text, trimmed, occupied)
}

/** Word-based locating (stage 4 of [locatePassage]); see the constants for its limits. */
object WordLocating {
    /** Shorter passages are only located literally — too few words to tell a match from a coincidence. */
    const val MIN_WORDS = 3

    /** Minimum share of agreeing words, relative to the longer of passage and window. */
    const val MIN_AGREEMENT = 0.8

    /** The window may be up to this many words shorter or longer than the passage (added or omitted words). */
    const val LENGTH_SLACK = 2

    /** Words are compared by their first characters, so inflected forms agree ("answers" / "answered"). */
    const val STEM_LENGTH = 5
}

private data class Word(val stem: String, val start: Int, val end: Int)

private fun words(text: String): List<Word> =
    Regex("[\\p{L}\\p{N}]+").findAll(text).map {
        Word(it.value.lowercase().take(WordLocating.STEM_LENGTH), it.range.first, it.range.last + 1)
    }.toList()

/**
 * Finds the window of words in [text] that agrees best with the words of [passage] (as multisets, so the order does
 * not matter), accepts it from [WordLocating.MIN_AGREEMENT], and trims words at its edges that do not occur in the
 * passage. Windows overlapping an [occupied] span are skipped. Ties go to the shorter, then the earlier window.
 */
fun locateByWords(text: String, passage: String, occupied: Set<TextSpan> = emptySet()): TextSpan? {
    val passageWords = words(passage).map { it.stem }
    if (passageWords.size < WordLocating.MIN_WORDS) return null
    val passageCounts = passageWords.groupingBy { it }.eachCount()
    val textWords = words(text)

    var best: Pair<Double, TextSpan>? = null
    val n = passageWords.size
    for (size in (n - WordLocating.LENGTH_SLACK).coerceAtLeast(1)..(n + WordLocating.LENGTH_SLACK)) {
        for (from in 0..textWords.size - size) {
            val window = textWords.subList(from, from + size)
            val windowCounts = window.groupingBy { it.stem }.eachCount()
            val agreeing = windowCounts.entries.sumOf { (stem, count) -> minOf(count, passageCounts[stem] ?: 0) }
            val agreement = agreeing.toDouble() / maxOf(n, size)
            if (agreement < WordLocating.MIN_AGREEMENT || agreement <= (best?.first ?: 0.0)) continue

            val trimmed = window.dropWhile { it.stem !in passageCounts }.dropLastWhile { it.stem !in passageCounts }
            val span = TextSpan(trimmed.first().start, trimmed.last().end)
            if (occupied.none { it.overlap(span) > 0 }) best = agreement to span
        }
    }
    return best?.second
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
    val (activityLabels, neutralLabels) = anchoredLabels.partition { it.kind == TextLabelKind.ACTIVITY }
    val located = locateActivities(processText, predictions)
    val matches = matchSpans(activityLabels, located, mode)

    val predictionByLabelId = matches.associate { match ->
        match.labelId to located.first { it.activity.id == match.predictionId }.activity
    }
    val matchedPredictionIds = matches.map { it.predictionId }.toSet()
    // Neutral labels only take predictions no activity label took, so they never compete with the activities
    val neutralPredictionIds = located.filter { prediction ->
        val span = prediction.span
        prediction.activity.id !in matchedPredictionIds && span != null &&
            neutralLabels.any { TextSpan(it.start, it.end).overlap(span) > 0 }
    }.map { it.activity.id }

    val truePositives = mutableListOf<String>()
    val falseNegatives = linkedMapOf<String, FalseNegativeCause>()
    val falsePositives = mutableListOf<String>()
    val trueNegatives = mutableListOf<String>()
    for (label in activityLabels.sortedBy { it.start }) {
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
            .filter { it.activity.isCritical && it.activity.id !in matchedPredictionIds && it.activity.id !in neutralPredictionIds }
            .map { it.activity.id },
        neutralPredictionIds = neutralPredictionIds,
        trueNegativeLabelIds = trueNegatives,
        ignoredLabelIds = orphanedLabels.map { it.id },
        labelCount = activityLabels.size
    )
}
