package de.mertendieckmann.griplbackend.ai

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.AiServices

object BaselineTextAnalysisAiServiceFactory {

    // Deliberately minimal, like the BPMN baseline prompt; only the verbatim quote is required, so the text scoring
    // can locate the activities in the text
    private val baselinePrompt: String = loadResource("prompts/system-prompt-text-baseline.txt")

    fun create(llm: ChatModel, memoryProvider: ChatMemoryProvider): BaselineTextAnalysisAiService =
        AiServices
            .builder(BaselineTextAnalysisAiService::class.java)
            .chatModel(llm)
            .chatMemoryProvider(memoryProvider)
            .systemMessageProvider { _ -> baselinePrompt }
            .build()

    private fun loadResource(path: String): String =
        checkNotNull(
            BaselineTextAnalysisAiServiceFactory::class.java.classLoader.getResourceAsStream(path)
        ) { "Prompt resource not found: $path" }
            .bufferedReader()
            .readText()
            .trim()
}
