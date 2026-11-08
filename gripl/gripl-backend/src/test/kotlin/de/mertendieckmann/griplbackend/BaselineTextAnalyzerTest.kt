package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.application.analyzer.BaselineTextAnalyzer
import de.mertendieckmann.griplbackend.evaluation.text.MatchMode
import de.mertendieckmann.griplbackend.evaluation.text.scoreText
import de.mertendieckmann.griplbackend.evaluation.text.textPredictions
import de.mertendieckmann.griplbackend.model.dto.TextLabel
import de.mertendieckmann.griplbackend.model.text.TextBaselineResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The text baseline answer must map to the same response shape as the two-step text pipeline. */
class BaselineTextAnalyzerTest {

    private val result = TextBaselineResult(listOf(
        TextBaselineResult.Activity("Fill in form", "The customer fills in the form.", true, "name and address"),
        TextBaselineResult.Activity("Archive case", "The case is archived.", false),
        TextBaselineResult.Activity("Send letter", "A letter is sent to the customer.", true, null),
    ))

    @Test
    fun `assigns ids in order and splits critical from all activities`() {
        val response = BaselineTextAnalyzer.toAnalysisResponse(result)

        assertEquals(listOf("activity-1", "activity-2", "activity-3"), response.extractedActivities!!.map { it.id })
        assertEquals(listOf("activity-1", "activity-3"), response.criticalElements.map { it.id })
        assertEquals("The customer fills in the form.", response.criticalElements[0].exactText)
        assertEquals("No reason provided", response.criticalElements[1].reason)
    }

    @Test
    fun `baseline response is scored like the text pipeline`() {
        val text = "The customer fills in the form. The case is archived. A letter is sent to the customer."
        fun label(id: String, passage: String, critical: Boolean) =
            text.indexOf(passage).let { TextLabel(id, it, it + passage.length, passage, critical) }
        val labels = listOf(
            label("l1", "The customer fills in the form", true),
            label("l2", "The case is archived", false),
            label("l3", "A letter is sent to the customer", true),
        )

        val scored = scoreText(text, labels, textPredictions(BaselineTextAnalyzer.toAnalysisResponse(result)), MatchMode.PARTIAL)

        assertEquals(listOf("l1", "l3"), scored.truePositiveLabelIds)
        assertEquals(listOf("l2"), scored.trueNegativeLabelIds)
        assertEquals(1.0, scored.extractionRecall)
    }
}
