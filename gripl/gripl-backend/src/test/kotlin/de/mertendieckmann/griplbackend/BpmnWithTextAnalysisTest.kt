package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.adapter.rag.RagApiClient
import de.mertendieckmann.griplbackend.application.BpmnExtractor
import de.mertendieckmann.griplbackend.application.ProcessDescriptionContext
import de.mertendieckmann.griplbackend.application.analyzer.BaselineBpmnAnalyzer
import de.mertendieckmann.griplbackend.application.analyzer.PromptBpmnAnalyzer
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Model-centric Combi Analysis BPMN+TEXT: the BPMN+TEXT prompt must equal the BPMN-only prompt plus the process description
 * section — and the BPMN-only prompt must stay exactly as before.
 */
class BpmnWithTextAnalysisTest {

    private val bpmnXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="test">
          <process id="Process_1">
            <task id="Task_1" name="Record patient name" />
            <task id="Task_2" name="Archive case" />
          </process>
        </definitions>
    """.trimIndent()
    private val processText = "The nurse records the name and diagnosis of the patient. The case is archived."

    /** Fake LLM that records the user messages and answers with an empty analysis result. */
    private class RecordingChatModel : ChatModel {
        val userMessages = mutableListOf<String>()
        override fun doChat(chatRequest: ChatRequest): ChatResponse {
            userMessages += chatRequest.messages().filterIsInstance<UserMessage>().last().singleText()
            return ChatResponse.builder().aiMessage(AiMessage.from("""{"elements": []}""")).build()
        }
    }

    private val elementsString = BpmnExtractor().extractBpmnElements(bpmnXml).toString()

    /**
     * The fake model has no structured output support, so langchain4j appends its JSON format instructions to the
     * user message. Compares the prompts without that shared suffix: BPMN-only must start with [bpmnPrompt], BPMN+TEXT
     * must be the same prompt with only the process description section inserted before the suffix.
     */
    private fun assertOnlySectionAdded(bpmnPrompt: String, bpmnOnly: String, withText: String) {
        assertTrue(bpmnOnly.startsWith(bpmnPrompt), "BPMN-only prompt changed")
        val formatSuffix = bpmnOnly.removePrefix(bpmnPrompt)
        assertEquals(ProcessDescriptionContext.appendTo(bpmnPrompt, processText) + formatSuffix, withText)
        assertTrue(withText.contains("=== PROCESS DESCRIPTION (ADDITIONAL CONTEXT) ==="))
    }

    @Test
    fun `GRIPL BPMN+TEXT prompt is the BPMN-only prompt plus the process description section`() {
        val bpmnOnly = RecordingChatModel()
        PromptBpmnAnalyzer(bpmnOnly, mock(RagApiClient::class.java)).analyzeBpmnForGdpr(bpmnXml)
        val withText = RecordingChatModel()
        PromptBpmnAnalyzer(withText, mock(RagApiClient::class.java)).analyzeBpmnWithTextForGdpr(bpmnXml, processText)

        // The BPMN-only prompt is the element set rendered as before
        assertOnlySectionAdded(elementsString, bpmnOnly.userMessages.single(), withText.userMessages.single())
    }

    @Test
    fun `baseline BPMN+TEXT prompt is the raw XML plus the same section`() {
        val bpmnOnly = RecordingChatModel()
        BaselineBpmnAnalyzer(bpmnOnly).analyzeBpmnForGdpr(bpmnXml)
        val withText = RecordingChatModel()
        BaselineBpmnAnalyzer(withText).analyzeBpmnWithTextForGdpr(bpmnXml, processText)

        assertOnlySectionAdded(bpmnXml, bpmnOnly.userMessages.single(), withText.userMessages.single())
    }
}
