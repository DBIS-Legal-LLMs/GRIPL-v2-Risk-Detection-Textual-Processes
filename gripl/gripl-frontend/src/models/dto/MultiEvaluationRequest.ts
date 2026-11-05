// Input modality a test case is evaluated with (mirrors the backend enum EvaluationModality)
export type EvaluationModality = "BPMN" | "TEXT";

export const EVALUATION_MODALITIES: EvaluationModality[] = ["BPMN", "TEXT"];

export interface MultiEvaluationRequest {
    models: ModelRunConfig[];
    datasets: number[];
    evaluationDataIds?: number[];
    defaultEvaluationEndpoint: string;
    seed?: number;
    maxConcurrent: number | null;
    repetitions?: number;
    useRag: boolean;
    ragMode: string;
    evaluateRag: boolean;
    activitiesOnly: boolean;
    // Modalities every model is evaluated with; more than one adds the modality to the model label
    modalities: EvaluationModality[];
    // Only test cases providing all modalities, so the modalities are compared on the same processes
    pairedOnly: boolean;
}

export interface ModelRunConfig {
    label: string;
    evaluationEndpoint?: string | null;
    llmProps?: LlmPropsOverride | null;
}

export interface LlmPropsOverride {
    baseUrl?: string | null;
    modelName?: string | null;
    apiKey?: string | null;
    timeoutSeconds?: number | null;
}