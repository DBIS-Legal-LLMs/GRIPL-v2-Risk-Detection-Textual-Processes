package de.mertendieckmann.griplbackend.application.analyzer

import de.mertendieckmann.griplbackend.application.BpmnExtractor
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

    /**
     * With [bpmnXml] (text-centric (Combi Analysis TEXT+BPMN)), the BPMN model of the same process guides step 1 only: its
     * activities are a reference for the extraction, while step 2 classifies the extracted activities exactly as
     * without a model — so a comparison with the text-only analysis isolates the model's effect on the extraction.
     */
    fun analyzeTextForGdpr(
        processText: String,
        useRag: Boolean = false,
        ragMode: RagMode = RagMode.HYBRID,
        bpmnXml: String? = null
    ): AnalysisResponse {
        val extraction = extractor.extractActivities(processText, bpmnXml?.let { BpmnExtractor().extractBpmnElements(it) })
        val response = elementAnalyzer.analyzeElements(toBpmnElements(extraction), useRag, ragMode, activitiesOnly = true)

        // Attach the source passage of each critical activity, so the frontend can highlight it in the text,
        // and the full extraction, so the evaluation can score non-critical activities and the extraction itself
        val exactTextById = extraction.activities.associate { it.id to it.exactText }
        return response.copy(
            criticalElements = response.criticalElements.map { it.copy(exactText = exactTextById[it.id]) },
            extractedActivities = extraction.activities.map {
                AnalysisResponse.ExtractedActivity(id = it.id, name = it.name, exactText = it.exactText)
            }
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
