import {EvaluationModality} from "@/models/dto/MultiEvaluationRequest";

export interface EvaluationMetadataReport {
    type: "metadata";
    modelLabels: string[],
    modelTemperatures: (number | undefined)[];
    modelTopPs: (number | undefined)[];
    datasets: { id: number; name: string }[];
    timestamp: string;
    totalTestCases: number;
    defaultEvaluationEndpoint: string;
    seed: number;
    totalRepetitions?: number;
    activitiesOnly?: boolean;
    modalities?: EvaluationModality[];
    pairedOnly?: boolean;
    // Evaluated test cases per modality; totalTestCases counts the distinct test cases over all modalities
    totalTestCasesByModality?: Partial<Record<EvaluationModality, number>>;
    markdown: string;
}

export interface TestCaseRagMetrics {
    faithfulness: number | null;
    contextUtilization: number | null;
    sampleCount: number;
    failedCount: number;
}

export interface RagSummaryMetrics {
    faithfulnessMean: number | null;
    contextUtilizationMean: number | null;
    evaluatedTestCases: number;
    totalSamples: number;
    failedSamples: number;
}

export interface ElementTypeCounts {
    truePositives: number;
    falsePositives: number;
    falseNegatives: number;
    trueNegatives: number;
}

export interface ElementTypeSummary extends ElementTypeCounts {
    displayName: string;
    precision: number;
    recall: number;
    f1Score: number;
    accuracy: number;
}

export interface TestCaseReport {
    type: "testCase";
    testCaseId: number;
    datasetId: number;
    testCaseName?: string;
    // SVG preview of the BPMN model; null for text test cases (see textResult)
    imageSrc: string | null;
    correctActivityIds: string[];
    falsePositiveIds: string[];
    falseNegativeIds: string[];
    expectedNamesWithIds: string[],
    actualNamesWithIds: string[];
    isSuccessful: boolean;
    result: { value: string; reason?: string }[];
    amountOfRetries: number | null;
    ragMetrics?: TestCaseRagMetrics | null;
    ragPromptContext?: string[] | null;
    perElementType?: Record<string, ElementTypeCounts>;
    // Text modality only. Then correctActivityIds/falseNegativeIds hold label ids and falsePositiveIds the ids of
    // the predictions (extracted activities) that caused a false positive
    textResult?: TextTestCaseResult;
    markdown: string;
}

export type TextLabelOutcome = "TP" | "FP" | "FN" | "TN" | "IGNORED";
export type FalseNegativeCause = "EXTRACTION" | "CLASSIFICATION";

// Metrics of the text scoring with strict matching (IoU >= 0.5)
export interface TextStrictResult extends ElementTypeCounts {
    precision: number;
    recall: number;
    f1Score: number;
    accuracy: number;
}

export interface TextLabelResult {
    id: string;
    start: number;
    end: number;
    text: string;
    isCritical: boolean;
    reason?: string | null;
    // outcome with partial matching (the main measure)
    outcome: TextLabelOutcome;
    falseNegativeCause?: FalseNegativeCause | null;
    matchedPredictionId?: string | null;
    iou?: number | null;
    strictOutcome: TextLabelOutcome;
}

export interface TextPredictionResult {
    id: string;
    name?: string | null;
    exactText: string;
    isCritical: boolean;
    reason?: string | null;
    // position of the quoted passage in the text; null if it could not be located
    start?: number | null;
    end?: number | null;
    // label it is matched to with partial matching
    matchedLabelId?: string | null;
}

export interface TextTestCaseResult {
    processText: string;
    labels: TextLabelResult[];
    predictions: TextPredictionResult[];
    extractionPrecision: number;
    extractionRecall: number;
    extractionF1: number;
    localizationRate: number;
    meanIou: number;
    falseNegativesByExtraction: number;
    falseNegativesByClassification: number;
    strict: TextStrictResult;
}

export interface TextSummaryMetrics {
    evaluatedTestCases: number;
    labelCount: number;
    ignoredLabelCount: number;
    extractionPrecision: number;
    extractionRecall: number;
    extractionF1: number;
    localizationRate: number;
    meanIou: number;
    falseNegativesByExtraction: number;
    falseNegativesByClassification: number;
    strict: TextStrictResult;
}

export interface EvaluationReportSummary {
    type: "summary";
    total: number;
    passed: number;
    failed: number;
    error: number;
    amountOfRetries: number | null;
    precision: number;
    recall: number;
    f1Score: number;
    accuracy: number;
    totalTruePositives: number;
    totalFalsePositives: number;
    totalFalseNegatives: number;
    totalTrueNegatives: number;
    ragMetrics?: RagSummaryMetrics | null;
    perElementType?: Record<string, ElementTypeSummary>;
    // Text modality only: extraction metrics and the metrics with strict matching
    textMetrics?: TextSummaryMetrics;
    markdown: string;
}

export interface EvaluationReportStepInfo {
    type: "stepInfo";
    currentTestCaseName: string;
    currentTestCaseId: number;
    currentTestCaseNumber: number;
    totalTestCases: number;
    markdown: string;
}

export interface EvaluationReportError {
    type: "error";
    testCaseId: number;
    datasetId?: number;
    testCaseName?: string;
    errorMessage: string;
    markdown: string;
}

export type EvaluationReport = EvaluationMetadataReport | TestCaseReport | EvaluationReportSummary | EvaluationReportStepInfo | EvaluationReportError;