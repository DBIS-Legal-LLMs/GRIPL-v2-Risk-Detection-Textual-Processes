package de.mertendieckmann.griplbackend.model.dto

/**
 * Input modality a test case is evaluated with. [label] is appended to the model label when a run compares
 * several modalities (e.g. "GPT-4o [TEXT]"), so the existing per-model charts compare the modalities.
 */
enum class EvaluationModality(val label: String) {
    /** The BPMN 2.0 model, scored by element ids against the BPMN labels. */
    BPMN("BPMN"),

    /** The textual process description, scored by text position against the text labels. */
    TEXT("TEXT"),

    /** Model-centric combination: the BPMN model with the text as additional context, scored like [BPMN]. */
    BPMN_WITH_TEXT("BPMN+TEXT"),

    /** Text-centric combination: the text with the model's activities as a reference, scored like [TEXT]. */
    TEXT_WITH_BPMN("TEXT+BPMN");

    /** Whether results are scored against the text labels (by position) instead of the BPMN labels (by element id). */
    val scoredOnText: Boolean get() = this == TEXT || this == TEXT_WITH_BPMN

    /** Whether [testCase] can be evaluated with this modality. */
    fun isAvailableFor(testCase: EvaluationData): Boolean = when (this) {
        BPMN -> testCase.bpmnXml != null
        // A text without labels has no ground truth to score against
        TEXT -> !testCase.processText.isNullOrBlank() && testCase.textLabels.isNotEmpty()
        // Scored on the BPMN labels, the text is context only
        BPMN_WITH_TEXT -> testCase.bpmnXml != null && !testCase.processText.isNullOrBlank()
        // Scored on the text labels, the model is a reference only
        TEXT_WITH_BPMN -> TEXT.isAvailableFor(testCase) && testCase.bpmnXml != null
    }
}
