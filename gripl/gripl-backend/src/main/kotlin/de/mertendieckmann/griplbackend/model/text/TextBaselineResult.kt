package de.mertendieckmann.griplbackend.model.text

import jdk.jfr.Description

/**
 * Result of the text baseline: a single LLM call that extracts the activities of a free-text process description
 * and classifies them at once — the text counterpart of [de.mertendieckmann.griplbackend.application.analyzer.BaselineBpmnAnalyzer].
 * Unlike that one, it returns every activity (not only the critical ones), so the text scoring can compute the
 * extraction metrics and true negatives for the baseline just like for the two-step GRIPL text pipeline.
 */
data class TextBaselineResult(
    @Description("All distinct process activities described in the text, in the order they appear")
    val activities: List<Activity>
) {
    @Description("A single process activity described in the text")
    data class Activity(
        @Description("A short human-readable name of the activity (max ~8 words)")
        val name: String,
        @Description("The exact, verbatim sentence or phrase copied from the text that describes this activity — not paraphrased")
        val exactText: String,
        @Description("Whether the activity processes personal data")
        val processesPersonalData: Boolean,
        @Description("Why the activity processes personal data; only for activities that do")
        val reason: String? = null
    )
}
