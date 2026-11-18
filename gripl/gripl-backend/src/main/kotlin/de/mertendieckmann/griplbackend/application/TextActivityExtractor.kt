package de.mertendieckmann.griplbackend.application

import de.mertendieckmann.griplbackend.ai.SharedChatMemoryProvider
import de.mertendieckmann.griplbackend.ai.TextActivityExtractionAiServiceFactory
import de.mertendieckmann.griplbackend.model.BpmnElement
import de.mertendieckmann.griplbackend.model.text.TextExtractionResult
import dev.langchain4j.model.chat.ChatModel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Step 1 of the text-analysis pipeline: LLM-driven counterpart to [BpmnExtractor].
 *
 * Where [BpmnExtractor] deterministically parses a fixed set of [de.mertendieckmann.griplbackend.model.BpmnElement]s
 * out of BPMN XML, this class asks the LLM to identify the equivalent "elements" (process activities) out of a
 * free-text process description, since plain text has no structural markup to parse them from.
 *
 * No RAG. An output that cannot be parsed is retried with a fresh call ([retryOnOutputParsingFailure]), as often as
 * the [SafetyNet] retries the classification.
 */
class TextActivityExtractor(
    private val llm: ChatModel
) {
    private val log = KotlinLogging.logger { }
    private val memoryProvider = SharedChatMemoryProvider(50)
    private val extractionAiService = TextActivityExtractionAiServiceFactory.create(llm, memoryProvider)

    /**
     * With [bpmnElements] (text-centric (Combi Analysis TEXT+BPMN)), the model's activities are appended to the text as a
     * reference ([ProcessModelContext]); without them, the call is exactly the text-only extraction.
     */
    fun extractActivities(processText: String, bpmnElements: Set<BpmnElement>? = null): TextExtractionResult =
        extractActivitiesWithRetries(processText, bpmnElements).first

    /** Like [extractActivities], plus the number of retries the extraction took. */
    fun extractActivitiesWithRetries(
        processText: String,
        bpmnElements: Set<BpmnElement>? = null
    ): Pair<TextExtractionResult, Int> {
        val userMessage = bpmnElements?.let { ProcessModelContext.appendTo(processText, it) } ?: processText
        val result = retryOnOutputParsingFailure {
            // A new session per attempt, so a retry does not see the broken output
            extractionAiService.extract(UUID.randomUUID().toString(), userMessage)
        }

        log.info { "Text Extraction Result: ${result.first} (retries: ${result.second})" }

        return result
    }
}
