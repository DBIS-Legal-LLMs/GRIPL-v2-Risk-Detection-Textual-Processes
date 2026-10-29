package de.mertendieckmann.griplbackend.ai

import de.mertendieckmann.griplbackend.model.text.TextExtractionResult
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage

interface TextActivityExtractionAiService {

    // Step 1 of the text-analysis pipeline: extract discrete activities from a free-text
    // process description. See PromptBpmnAnalysisAiService for the (future) Step 2 counterpart
    // that classifies the extracted activities for GDPR relevance.
    fun extract(
        @MemoryId sessionId: String,
        @UserMessage processText: String
    ): TextExtractionResult
}
