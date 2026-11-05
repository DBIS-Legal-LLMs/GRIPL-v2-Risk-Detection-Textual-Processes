package de.mertendieckmann.griplbackend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty
import de.mertendieckmann.griplbackend.evaluation.text.FalseNegativeCause

/** Outcome of one text label in the confusion matrix; IGNORED for orphaned labels that are not scored. */
enum class TextLabelOutcome { TP, FP, FN, TN, IGNORED }

/**
 * Details of a text test case for reports and the frontend preview: the text, every label with its outcome and every
 * prediction (extracted activity) with its position, plus the text-specific metrics. Outcomes use partial matching
 * (the main measure); [TextLabelResult.strictOutcome] and [strict] show the result with strict matching.
 */
data class TextTestCaseResult(
    val processText: String,
    val labels: List<TextLabelResult>,
    val predictions: List<TextPredictionResult>,
    val extractionPrecision: Double,
    val extractionRecall: Double,
    val extractionF1: Double,
    val localizationRate: Double,
    val meanIou: Double,
    val falseNegativesByExtraction: Int,
    val falseNegativesByClassification: Int,
    val strict: TextStrictResult
)

data class TextLabelResult(
    val id: String,
    val start: Int,
    val end: Int,
    val text: String,
    @get:JsonProperty("isCritical")
    val isCritical: Boolean,
    val reason: String?,
    val outcome: TextLabelOutcome,
    val falseNegativeCause: FalseNegativeCause?,
    val matchedPredictionId: String?,
    val iou: Double?,
    val strictOutcome: TextLabelOutcome
)

data class TextPredictionResult(
    val id: String,
    val name: String?,
    val exactText: String,
    @get:JsonProperty("isCritical")
    val isCritical: Boolean,
    val reason: String?,
    /** Position of the quoted passage in the text, null if it could not be located. */
    val start: Int?,
    val end: Int?,
    val matchedLabelId: String?
)

data class TextStrictResult(
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val trueNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val accuracy: Double
)

/** Text-specific metrics of a summary, aggregated as micro average over all text test cases. */
data class TextSummaryMetrics(
    val evaluatedTestCases: Int,
    val labelCount: Int,
    val ignoredLabelCount: Int,
    val extractionPrecision: Double,
    val extractionRecall: Double,
    val extractionF1: Double,
    val localizationRate: Double,
    val meanIou: Double,
    val falseNegativesByExtraction: Int,
    val falseNegativesByClassification: Int,
    val strict: TextStrictResult
)
