package de.mertendieckmann.griplbackend.application.analyzer

import de.mertendieckmann.griplbackend.application.TextActivityExtractor
import de.mertendieckmann.griplbackend.model.BpmnElement
import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.dto.RagMode
import de.mertendieckmann.griplbackend.model.text.TextExtractionResult

/**
 * Two-step text analysis:
 * 1. [TextActivityExtractor] extracts activities with synthetic IDs from the text.
 * 2. The activities are mapped to [BpmnElement]s and classified by the unchanged BPMN pipeline
 *    ([PromptBpmnAnalyzer.analyzeElements]) — same prompt, RAG, SafetyNet and ID resolution.
 *
 * Only activities are extracted, so classification always runs in activities-only mode.
 */
class TextAnalyzer(
    private val extractor: TextActivityExtractor,
    private val elementAnalyzer: PromptBpmnAnalyzer
) {

    fun analyzeTextForGdpr(processText: String, useRag: Boolean = false, ragMode: RagMode = RagMode.HYBRID): AnalysisResponse {
        val extraction = extractor.extractActivities(processText)
        val response = elementAnalyzer.analyzeElements(toBpmnElements(extraction), useRag, ragMode, activitiesOnly = true)

        // Attach the source passage of each critical activity, so the frontend can highlight it in the text
        val exactTextById = extraction.activities.associate { it.id to it.exactText }
        return response.copy(
            criticalElements = response.criticalElements.map { it.copy(exactText = exactTextById[it.id]) }
        )
    }

    companion object {
        fun toBpmnElements(extraction: TextExtractionResult): Set<BpmnElement> =
            extraction.activities.map {
                BpmnElement(
                    type = "task",
                    id = it.id,
                    name = it.name,
                    documentation = it.exactText,
                    isActivity = true
                )
            }.toSet()
    }
}
