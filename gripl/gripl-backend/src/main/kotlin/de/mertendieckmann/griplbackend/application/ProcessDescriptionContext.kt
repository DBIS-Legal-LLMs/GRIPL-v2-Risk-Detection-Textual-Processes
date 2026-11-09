package de.mertendieckmann.griplbackend.application

/**
 * Model-centric Combi Analysis BPMN+TEXT: the textual description is appended to the user message of a BPMN
 * analysis as additional context, while the BPMN model still defines which elements are classified. GRIPL and the
 * baseline use the same section, so a BPMN+TEXT prompt differs from the BPMN-only prompt by this section only.
 */
object ProcessDescriptionContext {

    private val header: String = checkNotNull(
        ProcessDescriptionContext::class.java.classLoader.getResourceAsStream("prompts/process-description-context.txt")
    ) { "Prompt resource not found: prompts/process-description-context.txt" }
        .bufferedReader()
        .readText()
        .trim()

    /** Appends the process description section to [prompt]. */
    fun appendTo(prompt: String, processText: String): String = "$prompt\n\n$header\n\n${processText.trim()}"
}
