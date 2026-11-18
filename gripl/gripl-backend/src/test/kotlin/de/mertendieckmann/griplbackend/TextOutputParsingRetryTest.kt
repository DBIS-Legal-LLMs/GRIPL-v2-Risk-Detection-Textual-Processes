package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.adapter.rag.RagApiClient
import de.mertendieckmann.griplbackend.application.MAX_OUTPUT_PARSING_RETRIES
import de.mertendieckmann.griplbackend.application.TextActivityExtractor
import de.mertendieckmann.griplbackend.application.analyzer.BaselineTextAnalyzer
import de.mertendieckmann.griplbackend.application.analyzer.PromptBpmnAnalyzer
import de.mertendieckmann.griplbackend.application.analyzer.TextAnalyzer
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.output.OutputParsingException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

/**
 * An extraction (or text baseline) output that cannot be parsed is retried with a fresh call, as often as the
 * SafetyNet retries the BPMN classification; the retries are reported in amountOfRetries.
 */
class TextOutputParsingRetryTest {

    private val processText = "The nurse records the name of the patient. The case is archived."

    /** Fake LLM that answers with the given responses in order and records every request. */
    private class ScriptedChatModel(private vararg val responses: String) : ChatModel {
        val requests = mutableListOf<ChatRequest>()
        override fun doChat(chatRequest: ChatRequest): ChatResponse {
            requests += chatRequest
            return ChatResponse.builder().aiMessage(AiMessage.from(responses[requests.size - 1])).build()
        }
    }

    // Broken like the gpt-oss-20b outputs of the first combined run: stray tokens instead of a key
    private val brokenExtraction = """{"activities": [{"id": "activity-1", "name": "Record", ców "{ typeofalign? ??": "x"}]}"""
    private val extraction = """{"activities": [
        {"id": "activity-1", "name": "Record patient name", "exactText": "The nurse records the name of the patient."},
        {"id": "activity-2", "name": "Archive case", "exactText": "The case is archived."}]}"""

    @Test
    fun `broken extraction is retried with a fresh call`() {
        val llm = ScriptedChatModel(brokenExtraction, brokenExtraction, extraction)

        val (result, retries) = TextActivityExtractor(llm).extractActivitiesWithRetries(processText)

        assertEquals(2, retries)
        assertEquals(listOf("activity-1", "activity-2"), result.activities.map { it.id })
        // A fresh conversation: the retry does not contain the broken answer
        assertTrue(llm.requests.last().messages().none { it is AiMessage })
        assertEquals(1, llm.requests.last().messages().filterIsInstance<UserMessage>().size)
    }

    @Test
    fun `extraction fails after the maximum number of retries`() {
        val llm = ScriptedChatModel(*Array(MAX_OUTPUT_PARSING_RETRIES + 1) { brokenExtraction })

        assertThrows<OutputParsingException> { TextActivityExtractor(llm).extractActivitiesWithRetries(processText) }
        assertEquals(MAX_OUTPUT_PARSING_RETRIES + 1, llm.requests.size)
    }

    @Test
    fun `text analysis reports the extraction retries`() {
        val llm = ScriptedChatModel(brokenExtraction, extraction, """{"elements": []}""")

        val response = TextAnalyzer(TextActivityExtractor(llm), PromptBpmnAnalyzer(llm, mock(RagApiClient::class.java)))
            .analyzeTextForGdpr(processText)

        assertEquals(1, response.amountOfRetries)
        assertEquals(2, response.extractedActivities?.size)
    }

    @Test
    fun `text baseline is retried with a fresh call`() {
        val baseline = """{"activities": [{"name": "Record patient name",
            "exactText": "The nurse records the name of the patient.", "processesPersonalData": true, "reason": "Name"}]}"""
        val llm = ScriptedChatModel(brokenExtraction, baseline)

        val response = BaselineTextAnalyzer(llm).analyzeTextForGdpr(processText)

        assertEquals(1, response.amountOfRetries)
        assertEquals(listOf("activity-1"), response.criticalElements.map { it.id })
    }
}
