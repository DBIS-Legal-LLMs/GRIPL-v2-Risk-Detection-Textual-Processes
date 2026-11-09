// Input modality a test case is evaluated with (mirrors the backend enum EvaluationModality).
// BPMN_WITH_TEXT: model-centric, the BPMN model with the text as context, scored on the BPMN labels.
// TEXT_WITH_BPMN: text-centric, the text with the BPMN model as extraction reference, scored on the text labels.
export type EvaluationModality = "BPMN" | "TEXT" | "BPMN_WITH_TEXT" | "TEXT_WITH_BPMN";

export const EVALUATION_MODALITIES: EvaluationModality[] = ["BPMN", "TEXT", "BPMN_WITH_TEXT", "TEXT_WITH_BPMN"];

// Suffix of the run labels with several modalities, e.g. "GPT-4o [BPMN+TEXT]" (EvaluationModality.label in the backend)
export const MODALITY_LABELS: Record<EvaluationModality, string> = {
    BPMN: "BPMN",
    TEXT: "TEXT",
    BPMN_WITH_TEXT: "BPMN+TEXT",
    TEXT_WITH_BPMN: "TEXT+BPMN",
};

// Modalities scored on the text labels (text scoring); the others are scored on the BPMN labels
export const isScoredOnText = (modality: EvaluationModality) => modality === "TEXT" || modality === "TEXT_WITH_BPMN";

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