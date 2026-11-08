package de.mertendieckmann.griplbackend.evaluation.text

import de.mertendieckmann.griplbackend.application.analyzer.TextAnalyzer
import de.mertendieckmann.griplbackend.model.BpmnElement
import de.mertendieckmann.griplbackend.model.dto.*
import de.mertendieckmann.griplbackend.model.text.TextExtractionResult
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Glue between the text analysis response, the text scoring ([scoreText]) and the evaluation report.
 */

private val log = KotlinLogging.logger {}

const val PROMPT_ENGINEERING_ENDPOINT = "/gdpr/analysis/prompt-engineering"
const val BASELINE_ENDPOINT = "/gdpr/analysis/baseline"
const val TEXT_ANALYSIS_ENDPOINT = "/gdpr/analysis/text"
const val TEXT_BASELINE_ENDPOINT = "/gdpr/analysis/text-baseline"

// Each BPMN analysis approach and its text counterpart
private val TEXT_ENDPOINTS = mapOf(
    PROMPT_ENGINEERING_ENDPOINT to TEXT_ANALYSIS_ENDPOINT,
    BASELINE_ENDPOINT to TEXT_BASELINE_ENDPOINT,
)

/**
 * The text analysis endpoint for a model whose BPMN endpoint is [bpmnEndpoint]: the prompt-engineering approach maps
 * to the two-step text pipeline, the baseline to the text baseline (relative or absolute, keeping host and port).
 * Any other (custom) endpoint is rejected, as its text counterpart is unknown.
 */
fun textAnalysisEndpointFor(bpmnEndpoint: String): String {
    val endpoint = bpmnEndpoint.trimEnd('/')
    val (bpmnPath, textPath) = requireNotNull(TEXT_ENDPOINTS.entries.firstOrNull { endpoint.endsWith(it.key) }) {
        "The text modality is only available for the prompt-engineering analysis ($PROMPT_ENGINEERING_ENDPOINT) and " +
            "the baseline ($BASELINE_ENDPOINT), not for '$bpmnEndpoint'"
    }
    return endpoint.removeSuffix(bpmnPath) + textPath
}

/**
 * Predictions of a text analysis: every extracted activity, critical when the classification returned it.
 * Responses without [AnalysisResponse.extractedActivities] (older backends) fall back to the critical elements only;
 * then non-critical activities are unknown and the extraction metrics are not meaningful.
 */
fun textPredictions(response: AnalysisResponse): List<PredictedActivity> {
    val criticalIds = response.criticalElements.map { it.id }.toSet()
    val extracted = response.extractedActivities
    if (extracted == null) {
        log.warn { "Text analysis response has no extractedActivities - scoring the critical elements only" }
        return response.criticalElements.map { PredictedActivity(it.id, it.exactText.orEmpty(), true) }
    }
    return extracted.map { PredictedActivity(it.id, it.exactText, it.id in criticalIds) }
}

/** The extracted activities as BPMN elements, as the text analyzer classified them (used for Ragas queries). */
fun textBpmnElements(response: AnalysisResponse): Set<BpmnElement> =
    TextAnalyzer.toBpmnElements(TextExtractionResult(
        response.extractedActivities.orEmpty().map { TextExtractionResult.ExtractedActivity(it.id, it.name, it.exactText) }
    ))

/** Ids of the predictions causing a false positive: matched to a non-critical label, or critical without any label. */
fun falsePositivePredictionIds(result: TextScoringResult): List<String> {
    val falsePositiveLabels = result.falsePositiveLabelIds.toSet()
    return result.matches.filter { it.labelId in falsePositiveLabels }.map { it.predictionId } +
        result.unmatchedCriticalPredictionIds
}

private fun outcomeOf(result: TextScoringResult, labelId: String): TextLabelOutcome = when (labelId) {
    in result.truePositiveLabelIds -> TextLabelOutcome.TP
    in result.falseNegativeLabelIds -> TextLabelOutcome.FN
    in result.falsePositiveLabelIds -> TextLabelOutcome.FP
    in result.trueNegativeLabelIds -> TextLabelOutcome.TN
    else -> TextLabelOutcome.IGNORED
}

private fun ratio(numerator: Int, denominator: Int): Double =
    if (denominator > 0) numerator.toDouble() / denominator else 0.0

/** Report details of one text test case, from the scoring with partial and strict matching. */
fun buildTextTestCaseResult(
    processText: String,
    labels: List<TextLabel>,
    response: AnalysisResponse,
    partial: TextScoringResult,
    strict: TextScoringResult
): TextTestCaseResult {
    val matchByLabel = partial.matches.associateBy { it.labelId }
    val matchByPrediction = partial.matches.associateBy { it.predictionId }
    val criticalById = response.criticalElements.associateBy { it.id }
    val nameById = response.extractedActivities.orEmpty().associate { it.id to it.name }

    val strictPrecision = ratio(strict.truePositives, strict.truePositives + strict.falsePositives)
    val strictRecall = ratio(strict.truePositives, strict.truePositives + strict.falseNegatives)

    return TextTestCaseResult(
        processText = processText,
        labels = labels.sortedBy { it.start }.map { label ->
            val match = matchByLabel[label.id]
            TextLabelResult(
                id = label.id,
                start = label.start,
                end = label.end,
                text = label.text,
                isCritical = label.isCritical,
                reason = label.reason,
                outcome = outcomeOf(partial, label.id),
                falseNegativeCause = partial.falseNegativeLabelIds[label.id],
                matchedPredictionId = match?.predictionId,
                iou = match?.iou,
                strictOutcome = outcomeOf(strict, label.id)
            )
        },
        predictions = partial.located.map { located ->
            TextPredictionResult(
                id = located.activity.id,
                name = nameById[located.activity.id] ?: criticalById[located.activity.id]?.name,
                exactText = located.activity.exactText,
                isCritical = located.activity.isCritical,
                reason = criticalById[located.activity.id]?.reason,
                start = located.span?.start,
                end = located.span?.end,
                matchedLabelId = matchByPrediction[located.activity.id]?.labelId
            )
        },
        extractionPrecision = partial.extractionPrecision,
        extractionRecall = partial.extractionRecall,
        extractionF1 = partial.extractionF1,
        localizationRate = partial.localizationRate,
        meanIou = partial.meanIou,
        falseNegativesByExtraction = partial.falseNegativesByExtraction,
        falseNegativesByClassification = partial.falseNegativesByClassification,
        strict = TextStrictResult(
            truePositives = strict.truePositives,
            falsePositives = strict.falsePositives,
            falseNegatives = strict.falseNegatives,
            trueNegatives = strict.trueNegatives,
            precision = strictPrecision,
            recall = strictRecall,
            f1Score = if (strictPrecision + strictRecall > 0) 2 * strictPrecision * strictRecall / (strictPrecision + strictRecall) else 0.0,
            accuracy = ratio(strict.truePositives + strict.trueNegatives,
                strict.truePositives + strict.falsePositives + strict.falseNegatives + strict.trueNegatives)
        )
    )
}
