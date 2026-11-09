import {EVALUATION_MODALITIES, EvaluationModality, MODALITY_LABELS} from "@/models/dto/MultiEvaluationRequest";

const MODALITY_BY_LABEL = new Map(EVALUATION_MODALITIES.map((m) => [MODALITY_LABELS[m], m]));
const MODALITY_SUFFIX = /^(.*) \[([A-Z+]+)]$/;

// Splits a run label of an evaluation with several modalities, e.g. "GPT-4o [BPMN+TEXT]", into model and modality;
// null for labels without a modality suffix (evaluations with a single modality)
export function parseRunLabel(label: string): { model: string; modality: EvaluationModality } | null {
    const match = MODALITY_SUFFIX.exec(label);
    const modality = match && MODALITY_BY_LABEL.get(match[2]);
    return match && modality ? {model: match[1], modality} : null;
}
