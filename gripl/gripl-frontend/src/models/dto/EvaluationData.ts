// A test case represents one process: a BPMN model, a textual process description, or both (paired)
export interface EvaluationData {
    id: number,
    name: string,
    bpmnXml: string | null,
    expectedValues: ExpectedValues[],
    datasetId: number,
    processText: string | null,
    textLabels: TextLabel[]
}

export interface ExpectedValues {
    value: string
    reason?: string
}

// A labeled passage of the process text. Every activity is labeled, the GDPR-critical ones are flagged.
// start/end are character offsets into the process text (end exclusive), text is the labeled passage itself.
// NEUTRAL labels mark passages that are no activity in the BPMN model (event, gateway, message, data access):
// LLM activities there are not scored. Labels without kind are activities.
export type TextLabelKind = "ACTIVITY" | "NEUTRAL";

export interface TextLabel {
    id: string
    start: number
    end: number
    text: string
    isCritical: boolean
    reason?: string
    kind?: TextLabelKind
}

export interface EvaluationDataMeta {
    id: number,
    name?: string,
    datasetId?: number,
    hasBpmn: boolean,
    hasText: boolean
}
