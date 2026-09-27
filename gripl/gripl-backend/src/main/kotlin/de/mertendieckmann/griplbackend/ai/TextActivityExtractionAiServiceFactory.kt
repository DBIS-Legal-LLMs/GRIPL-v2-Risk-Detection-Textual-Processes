package de.mertendieckmann.griplbackend.ai

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.AiServices

object TextActivityExtractionAiServiceFactory {

    private val extractionPrompt: String = loadResource("prompts/system-prompt-text-extraction.txt")

    fun create(llm: ChatModel, memoryProvider: ChatMemoryProvider): TextActivityExtractionAiService =
        AiServices
            .builder(TextActivityExtractionAiService::class.java)
            .chatModel(llm)
            .chatMemoryProvider(memoryProvider)
            .systemMessageProvider { _ -> extractionPrompt }
            .build()

    private fun loadResource(path: String): String =
        checkNotNull(
            TextActivityExtractionAiServiceFactory::class.java.classLoader.getResourceAsStream(path)
        ) { "Prompt resource not found: $path" }
            .bufferedReader()
            .readText()
            .trim()
}
