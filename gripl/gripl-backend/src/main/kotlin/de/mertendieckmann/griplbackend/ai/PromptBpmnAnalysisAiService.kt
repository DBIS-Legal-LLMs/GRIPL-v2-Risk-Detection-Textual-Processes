package de.mertendieckmann.griplbackend.ai

import de.mertendieckmann.griplbackend.model.BpmnElement
import de.mertendieckmann.griplbackend.model.analysis.BpmnAnalysisResult
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage

interface PromptBpmnAnalysisAiService {

    
    // Analyse BPMN elements without RAG context.
    
    fun analyze(
        @MemoryId sessionId: String,
        @UserMessage bpmnElements: Set<BpmnElement>
    ): BpmnAnalysisResult

    /**
     * Analyse BPMN elements with RAG context injected.
     * [formattedPrompt] is a pre-built string that combines the retrieved
     * legal knowledge and the BPMN elements in a single message.
     */
    fun analyzeWithRagContext(
        @MemoryId sessionId: String,
        @UserMessage formattedPrompt: String
    ): BpmnAnalysisResult

    /**
     * (Combi Analysis BPMN+TEXT) Analyse BPMN elements without RAG context, with the textual process description appended.
     * [formattedPrompt] is the rendered element set followed by the process description section.
     */
    fun analyzeWithProcessText(
        @MemoryId sessionId: String,
        @UserMessage formattedPrompt: String
    ): BpmnAnalysisResult
}