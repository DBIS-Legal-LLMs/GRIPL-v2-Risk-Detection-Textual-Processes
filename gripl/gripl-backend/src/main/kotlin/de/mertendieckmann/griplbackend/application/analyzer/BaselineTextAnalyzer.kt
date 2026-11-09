package de.mertendieckmann.griplbackend.application.analyzer

import de.mertendieckmann.griplbackend.ai.BaselineTextAnalysisAiServiceFactory
import de.mertendieckmann.griplbackend.ai.SharedChatMemoryProvider
import de.mertendieckmann.griplbackend.application.BpmnExtractor
import de.mertendieckmann.griplbackend.application.ProcessModelContext
import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.text.TextBaselineResult
import dev.langchain4j.model.chat.ChatModel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

/**
 * Text baseline, the counterpart of [BaselineBpmnAnalyzer] for textual process descriptions: one LLM call with a
 * minimal prompt extracts and classifies the activities at once — no separate extraction step, no GRIPL
 * prompt engineering, no RAG. Returns the same response shape as [TextAnalyzer], so the evaluation scores both alike.
 */
class BaselineTextAnalyzer(
    llm: ChatModel
) {
    private val log = KotlinLogging.logger { }
    private val aiService = BaselineTextAnalysisAiServiceFactory.create(llm, SharedChatMemoryProvider(50))

    /**
     * With [bpmnXml](text-centric (Combi Analysis TEXT+BPMN)), the model's activities are appended to the text as a reference.
     * The baseline has a single call, so the reference affects extraction and classification alike.
     */
    fun analyzeTextForGdpr(processText: String, useRag: Boolean = false, bpmnXml: String? = null): AnalysisResponse {
        if (useRag) {
            log.warn { "Baseline text analyzer does not support RAG — useRag=true is ignored; results are baseline-only." }
        }
        val userMessage = bpmnXml?.let { ProcessModelContext.appendTo(processText, BpmnExtractor().extractBpmnElements(it)) }
            ?: processText
        val result = aiService.analyze(UUID.randomUUID().toString(), userMessage)
        log.info { "Text Baseline Result: $result" }
        return toAnalysisResponse(result)
    }

    companion object {
        /**
         * Maps the baseline result to the text analysis response: ids are assigned in order (activity-1, …) instead of
         * trusting the LLM, every activity goes into extractedActivities, the personal-data ones into criticalElements.
         */
        fun toAnalysisResponse(result: TextBaselineResult): AnalysisResponse {
            val activities = result.activities.mapIndexed { index, activity -> "activity-${index + 1}" to activity }
            return AnalysisResponse(
                criticalElements = activities
                    .filter { (_, activity) -> activity.processesPersonalData }
                    .map { (id, activity) ->
                        AnalysisResponse.CriticalElement(
                            id = id,
                            name = activity.name,
                            type = "task",
                            reason = activity.reason?.takeIf { it.isNotBlank() } ?: "No reason provided",
                            exactText = activity.exactText
                        )
                    },
                amountOfRetries = 0,
                extractedActivities = activities.map { (id, activity) ->
                    AnalysisResponse.ExtractedActivity(id = id, name = activity.name, exactText = activity.exactText)
                }
            )
        }
    }
}
