package de.mertendieckmann.griplbackend.application

import de.mertendieckmann.griplbackend.ai.SharedChatMemoryProvider
import de.mertendieckmann.griplbackend.ai.TextActivityExtractionAiServiceFactory
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
 * This is deliberately minimal for now (no RAG, no retry-on-parse-failure via SafetyNet) — it exists to test
 * extraction quality in isolation before Step 2 (GDPR classification, reusing the existing BPMN analysis
 * pipeline on the extracted activities) is wired up.
 */
class TextActivityExtractor(
    private val llm: ChatModel
) {
    private val log = KotlinLogging.logger { }
    private val memoryProvider = SharedChatMemoryProvider(50)
    private val extractionAiService = TextActivityExtractionAiServiceFactory.create(llm, memoryProvider)

    fun extractActivities(processText: String): TextExtractionResult {
        val sessionId = UUID.randomUUID().toString()
        val result = extractionAiService.extract(sessionId, processText)
        
        log.info { "Text Extraction Result: $result" }

        return result
    }
}
