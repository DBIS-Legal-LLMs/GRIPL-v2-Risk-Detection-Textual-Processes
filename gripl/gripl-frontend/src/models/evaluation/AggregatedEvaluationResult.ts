export interface AggregatedElementTypeResult {
    displayName: string
    avgPrecision: number
    stdPrecision: number
    avgRecall: number
    stdRecall: number
    avgF1Score: number
    stdF1Score: number
    avgTruePositives: number
    avgFalsePositives: number
    avgFalseNegatives: number
    avgTrueNegatives: number
    runsCounted: number
}

// Text-specific metrics of a text run label, mean and SD across the repetitions
export interface AggregatedTextResult {
    avgExtractionPrecision: number
    stdExtractionPrecision: number
    avgExtractionRecall: number
    stdExtractionRecall: number
    avgExtractionF1: number
    stdExtractionF1: number
    avgLocalizationRate: number
    stdLocalizationRate: number
    avgMeanIou: number
    stdMeanIou: number
    avgFalseNegativesByExtraction: number
    stdFalseNegativesByExtraction: number
    avgFalseNegativesByClassification: number
    stdFalseNegativesByClassification: number
    avgStrictPrecision: number
    stdStrictPrecision: number
    avgStrictRecall: number
    stdStrictRecall: number
    avgStrictF1Score: number
    stdStrictF1Score: number
    runsCounted: number
}

export interface AggregatedEvaluationResult {
    avgPrecision: number
    stdPrecision: number
    avgRecall: number
    stdRecall: number
    avgF1Score: number
    stdF1Score: number
    avgAccuracy: number
    stdAccuracy: number
    avgPassed: number
    stdPassed: number
    avgFailed: number
    stdFailed: number
    avgErrors: number
    stdErrors: number
    avgTruePositives: number
    stdTruePositives: number
    avgFalsePositives: number
    stdFalsePositives: number
    avgFalseNegatives: number
    stdFalseNegatives: number
    avgTrueNegatives: number
    stdTrueNegatives: number
    avgAmountOfRetries: number | undefined
    stdAmountOfRetries: number | undefined
    avgContextUtilization: number | undefined
    stdContextUtilization: number | undefined
    avgFaithfulness: number | undefined
    stdFaithfulness: number | undefined
    ragRunsCounted: number
    perElementType?: Record<string, AggregatedElementTypeResult>
    // Text modality only
    text?: AggregatedTextResult
}

export type AggregatedEvaluationResults = Record<string, AggregatedEvaluationResult>;