package de.mertendieckmann.griplbackend.application.analyzer

import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.dto.RagMode

interface BpmnAnalyzer {

    /**
     * @param activitiesOnly When true, only activities/tasks are classified (the pre-"all elements"
     * behaviour): the LLM prompt is restricted to activities and no RAG context is retrieved for
     * other element types.
     */
    fun analyzeBpmnForGdpr(
        bpmnXml: String,
        useRag: Boolean = false,
        ragMode: RagMode = RagMode.HYBRID,
        activitiesOnly: Boolean = false
    ): AnalysisResponse

    /**
     * Model-centric Combi Analysis BPMN+TEXT: analyzes the BPMN model like [analyzeBpmnForGdpr], with the textual description
     * of the same process appended as additional context ([de.mertendieckmann.griplbackend.application.ProcessDescriptionContext]).
     * The BPMN model defines which elements are classified.
     */
    fun analyzeBpmnWithTextForGdpr(
        bpmnXml: String,
        processText: String,
        useRag: Boolean = false,
        ragMode: RagMode = RagMode.HYBRID,
        activitiesOnly: Boolean = false
    ): AnalysisResponse
}