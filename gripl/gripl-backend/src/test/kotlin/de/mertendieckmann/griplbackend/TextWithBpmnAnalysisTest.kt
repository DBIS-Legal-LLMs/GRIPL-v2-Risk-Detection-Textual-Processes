package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.adapter.rag.RagApiClient
import de.mertendieckmann.griplbackend.application.BpmnExtractor
import de.mertendieckmann.griplbackend.application.ProcessModelContext
import de.mertendieckmann.griplbackend.application.TextActivityExtractor
import de.mertendieckmann.griplbackend.application.analyzer.BaselineTextAnalyzer
import de.mertendieckmann.griplbackend.application.analyzer.PromptBpmnAnalyzer
import de.mertendieckmann.griplbackend.application.analyzer.TextAnalyzer
import de.mertendieckmann.griplbackend.model.BpmnElement
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Text-centric (Combi Analysis TEXT+BPMN)): the TEXT+BPMN prompt must equal the text-only prompt plus the model section, only the
 * extraction (step 1) of the GRIPL pipeline gets the model, and the text-only prompts stay exactly as before.
 */
class TextWithBpmnAnalysisTest {

    private val bpmnXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="test">
          <process id="Process_1">
            <task id="Task_1" name="Record patient name" />
            <task id="Task_2" name="Archive case" />
          </process>
        </definitions>
    """.trimIndent()
    private val processText = "The nurse records the name of the patient. The case is archived."
    private val bpmnElements = BpmnExtractor().extractBpmnElements(bpmnXml)

    /** Fake LLM that records the user messages and answers with the given responses in order. */
    private class RecordingChatModel(private vararg val responses: String) : ChatModel {
        val userMessages = mutableListOf<String>()
        override fun doChat(chatRequest: ChatRequest): ChatResponse {
            userMessages += chatRequest.messages().filterIsInstance<UserMessage>().last().singleText()
            return ChatResponse.builder().aiMessage(AiMessage.from(responses[userMessages.size - 1])).build()
        }
    }

    private val extractionAnswer = """{"activities": [
        {"id": "activity-1", "name": "Record patient name", "exactText": "The nurse records the name of the patient."},
        {"id": "activity-2", "name": "Archive case", "exactText": "The case is archived."}]}"""
    private val classificationAnswer = """{"elements": []}"""

    private fun griplRun(withModel: Boolean): List<String> {
        val llm = RecordingChatModel(extractionAnswer, classificationAnswer)
        TextAnalyzer(TextActivityExtractor(llm), PromptBpmnAnalyzer(llm, mock(RagApiClient::class.java)))
            .analyzeTextForGdpr(processText, bpmnXml = if (withModel) bpmnXml else null)
        return llm.userMessages
    }

    // The fake model has no structured output support, so langchain4j appends its JSON format instructions to the
    // user message; the prompts are compared with that shared suffix.
    private fun assertOnlySectionAdded(textOnly: String, withModel: String) {
        assertTrue(textOnly.startsWith(processText), "text-only prompt changed")
        val formatSuffix = textOnly.removePrefix(processText)
        assertEquals(ProcessModelContext.appendTo(processText, bpmnElements) + formatSuffix, withModel)
    }

    @Test
    fun `GRIPL adds the model section to the extraction only`() {
        val textOnly = griplRun(withModel = false)
        val withModel = griplRun(withModel = true)

        assertEquals(2, withModel.size)
        assertOnlySectionAdded(textOnly[0], withModel[0])
        // Step 2 classifies the same extracted activities with exactly the same prompt
        assertEquals(textOnly[1], withModel[1])
    }

    @Test
    fun `baseline adds the same model section to its single call`() {
        val textOnly = RecordingChatModel("""{"activities": []}""")
        BaselineTextAnalyzer(textOnly).analyzeTextForGdpr(processText)
        val withModel = RecordingChatModel("""{"activities": []}""")
        BaselineTextAnalyzer(withModel).analyzeTextForGdpr(processText, bpmnXml = bpmnXml)

        assertOnlySectionAdded(textOnly.userMessages.single(), withModel.userMessages.single())
    }

    @Test
    fun `model section lists the named activities with their lane`() {
        val elements = setOf(
            BpmnElement(type = "task", id = "T1", name = "Record name", laneName = "Nurse", isActivity = true),
            BpmnElement(type = "task", id = "T2", name = null, isActivity = true),
            BpmnElement(type = "exclusiveGateway", id = "G1", name = "Approved?"),
            BpmnElement(type = "task", id = "T3", name = "Send letter", isActivity = true),
        )
        val section = ProcessModelContext.appendTo("Text.", elements)
        assertTrue(section.startsWith("Text.\n\n=== BPMN PROCESS MODEL (ADDITIONAL CONTEXT) ==="))
        assertTrue(section.endsWith("- Record name (lane: Nurse)\n- Send letter"))
        assertFalse(section.contains("Approved?"))
    }
}
