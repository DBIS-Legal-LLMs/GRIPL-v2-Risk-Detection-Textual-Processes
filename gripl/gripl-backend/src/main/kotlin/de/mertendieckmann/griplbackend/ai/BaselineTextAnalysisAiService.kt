package de.mertendieckmann.griplbackend.ai

import de.mertendieckmann.griplbackend.model.text.TextBaselineResult
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage

interface BaselineTextAnalysisAiService {

    // Text baseline: extracts and classifies the activities of a free-text process description in a single call
    fun analyze(
        @MemoryId sessionId: String,
        @UserMessage processText: String
    ): TextBaselineResult
}
