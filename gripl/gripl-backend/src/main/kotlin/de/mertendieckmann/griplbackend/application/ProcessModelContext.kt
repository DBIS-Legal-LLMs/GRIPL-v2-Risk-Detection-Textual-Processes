package de.mertendieckmann.griplbackend.application

import de.mertendieckmann.griplbackend.model.BpmnElement

/**
 * Text-centric (Combi Analysis TEXT+BPMN): the activities of the BPMN model are appended to the process text as
 * a reference for which activities the process contains, while the activities are still taken from the text. GRIPL
 * (extraction step) and the text baseline use the same section, so a TEXT+BPMN prompt differs from the text-only
 * prompt by this section only.
 */
object ProcessModelContext {

    private val header: String = checkNotNull(
        ProcessModelContext::class.java.classLoader.getResourceAsStream("prompts/process-model-context.txt")
    ) { "Prompt resource not found: prompts/process-model-context.txt" }
        .bufferedReader()
        .readText()
        .trim()

    /** Appends the model's named activities (in model order, with their lane) to [processText]. */
    fun appendTo(processText: String, bpmnElements: Set<BpmnElement>): String {
        val activities = bpmnElements
            .filter { it.isActivity && !it.name.isNullOrBlank() }
            .joinToString("\n") { activity ->
                "- ${activity.name!!.trim()}" + (activity.laneName?.takeIf { it.isNotBlank() }?.let { " (lane: $it)" } ?: "")
            }
        return "$processText\n\n$header\n\n${activities.ifEmpty { "(no named activities)" }}"
    }
}
