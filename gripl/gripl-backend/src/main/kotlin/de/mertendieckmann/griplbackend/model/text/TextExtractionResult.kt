package de.mertendieckmann.griplbackend.model.text

import jdk.jfr.Description

/**
 * Result of Step 1 ("extraction") of the text-analysis pipeline: turns a free-text process
 * description into a flat list of discrete process activities with stable synthetic IDs.
 *
 * This is the text-modality counterpart to what [de.mertendieckmann.griplbackend.application.BpmnExtractor]
 * does deterministically for BPMN XML — except here the "extraction" itself is done by the LLM,
 * since natural language has no structural markup to parse activities out of.
 *
 * The resulting [ExtractedActivity] list is intended to be fed into Step 2 (GDPR classification)
 * by mapping each activity into a synthetic [de.mertendieckmann.griplbackend.model.BpmnElement]
 * (type = "task", isActivity = true, documentation = exactText) so the existing
 * [de.mertendieckmann.griplbackend.ai.PromptBpmnAnalysisAiService] can be reused unchanged.
 */
data class TextExtractionResult(
    @Description("List of distinct process activities/steps identified in the text")
    val activities: List<ExtractedActivity>
) {
    @Description("A single process activity/step identified in a textual process description")
    data class ExtractedActivity(
        @Description("A short, stable, unique identifier for this activity, e.g. \"activity-1\". Must be unique within the response.")
        val id: String,
        @Description("A short human-readable label for the activity (max ~8 words), e.g. \"Verify customer identity\".")
        val name: String,
        @Description("The exact, verbatim sentence or phrase copied from the source text that describes this activity — not paraphrased.")
        val exactText: String
    )
}
