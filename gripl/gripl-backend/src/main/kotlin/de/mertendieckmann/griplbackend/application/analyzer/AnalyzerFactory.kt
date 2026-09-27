package de.mertendieckmann.griplbackend.application.analyzer

import de.mertendieckmann.griplbackend.adapter.rag.RagApiClient
import de.mertendieckmann.griplbackend.application.TextActivityExtractor
import dev.langchain4j.model.chat.ChatModel
import org.springframework.stereotype.Component

@Component
class AnalyzerFactory(
    private val ragApiClient: RagApiClient
) {
    fun createPromptEngineeringAnalyzer(chatModel: ChatModel): PromptBpmnAnalyzer {
        return PromptBpmnAnalyzer(chatModel, ragApiClient)
    }

    fun createBaselineAnalyzer(chatModel: ChatModel): BaselineBpmnAnalyzer {
        return BaselineBpmnAnalyzer(chatModel)
    }

    // Step 1 of the (in-progress) text-analysis pipeline — see TextActivityExtractor.
    fun createTextActivityExtractor(chatModel: ChatModel): TextActivityExtractor {
        return TextActivityExtractor(chatModel)
    }
}