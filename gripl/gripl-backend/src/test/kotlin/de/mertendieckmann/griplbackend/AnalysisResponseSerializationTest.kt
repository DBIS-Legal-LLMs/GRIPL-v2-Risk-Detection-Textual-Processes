package de.mertendieckmann.griplbackend

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The text-only fields of [AnalysisResponse] must not appear in BPMN responses, so the BPMN API stays unchanged.
 */
class AnalysisResponseSerializationTest {

    private val mapper = jacksonObjectMapper()

    private val criticalElement = AnalysisResponse.CriticalElement(id = "Activity_1", name = "Check data", reason = "personal data")

    @Test
    fun `BPMN response omits text-only fields`() {
        val json = mapper.readTree(mapper.writeValueAsString(AnalysisResponse(criticalElements = listOf(criticalElement))))

        assertFalse(json.has("extractedActivities"))
        assertFalse(json["criticalElements"][0].has("exactText"))
    }

    @Test
    fun `text response includes all extracted activities`() {
        val response = AnalysisResponse(
            criticalElements = listOf(criticalElement.copy(id = "activity-1", exactText = "The clerk checks the data.")),
            extractedActivities = listOf(
                AnalysisResponse.ExtractedActivity("activity-1", "Check data", "The clerk checks the data."),
                AnalysisResponse.ExtractedActivity("activity-2", "Send letter", "A letter is sent.")
            )
        )

        val json = mapper.readTree(mapper.writeValueAsString(response))

        assertEquals(2, json["extractedActivities"].size())
        assertEquals("activity-2", json["extractedActivities"][1]["id"].asText())
        assertEquals("A letter is sent.", json["extractedActivities"][1]["exactText"].asText())
    }
}
