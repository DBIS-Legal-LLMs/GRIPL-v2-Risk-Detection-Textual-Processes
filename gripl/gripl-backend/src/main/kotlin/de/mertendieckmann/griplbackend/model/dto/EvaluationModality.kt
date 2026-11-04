package de.mertendieckmann.griplbackend.model.dto

/**
 * Input modality a test case is evaluated with. [label] is appended to the model label when a run compares
 * several modalities (e.g. "GPT-4o [TEXT]"), so the existing per-model charts compare the modalities.
 */
enum class EvaluationModality(val label: String) {
    /** The BPMN 2.0 model, scored by element ids against the BPMN labels. */
    BPMN("BPMN"),

    /** The textual process description, scored by text position against the text labels. */
    TEXT("TEXT");

    /** Whether [testCase] can be evaluated with this modality. */
    fun isAvailableFor(testCase: EvaluationData): Boolean = when (this) {
        BPMN -> testCase.bpmnXml != null
        // A text without labels has no ground truth to score against
        TEXT -> !testCase.processText.isNullOrBlank() && testCase.textLabels.isNotEmpty()
    }
}
