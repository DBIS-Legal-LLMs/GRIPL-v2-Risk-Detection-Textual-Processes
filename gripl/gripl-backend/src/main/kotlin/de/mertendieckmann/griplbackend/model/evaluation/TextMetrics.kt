package de.mertendieckmann.griplbackend.model.evaluation

import de.mertendieckmann.griplbackend.evaluation.text.TextScoringResult

/**
 * Text-specific counts of one test case, or summed over several test cases. The main confusion counts of the text
 * modality (partial matching) live in [EvaluationMetrics] like for BPMN; this holds what text adds on top: the
 * extraction counts and the confusion counts with strict matching (IoU >= 0.5).
 */
data class TextMetrics(
    val labelCount: Int = 0,
    val ignoredLabelCount: Int = 0,
    val predictedCount: Int = 0,
    val localizedCount: Int = 0,
    val matchedCount: Int = 0,
    val iouSum: Double = 0.0,
    val falseNegativesByExtraction: Int = 0,
    val falseNegativesByClassification: Int = 0,
    val strict: ElementTypeCounts = ElementTypeCounts(),
    val strictMatchedCount: Int = 0,
    val strictIouSum: Double = 0.0
) {
    operator fun plus(other: TextMetrics) = TextMetrics(
        labelCount = labelCount + other.labelCount,
        ignoredLabelCount = ignoredLabelCount + other.ignoredLabelCount,
        predictedCount = predictedCount + other.predictedCount,
        localizedCount = localizedCount + other.localizedCount,
        matchedCount = matchedCount + other.matchedCount,
        iouSum = iouSum + other.iouSum,
        falseNegativesByExtraction = falseNegativesByExtraction + other.falseNegativesByExtraction,
        falseNegativesByClassification = falseNegativesByClassification + other.falseNegativesByClassification,
        strict = strict + other.strict,
        strictMatchedCount = strictMatchedCount + other.strictMatchedCount,
        strictIouSum = strictIouSum + other.strictIouSum
    )

    companion object {
        fun from(partial: TextScoringResult, strict: TextScoringResult) = TextMetrics(
            labelCount = partial.labelCount,
            ignoredLabelCount = partial.ignoredLabelIds.size,
            predictedCount = partial.predictedCount,
            localizedCount = partial.localizedCount,
            matchedCount = partial.matchedCount,
            iouSum = partial.matches.sumOf { it.iou },
            falseNegativesByExtraction = partial.falseNegativesByExtraction,
            falseNegativesByClassification = partial.falseNegativesByClassification,
            strict = ElementTypeCounts(strict.truePositives, strict.falsePositives, strict.falseNegatives, strict.trueNegatives),
            strictMatchedCount = strict.matchedCount,
            strictIouSum = strict.matches.sumOf { it.iou }
        )
    }
}
