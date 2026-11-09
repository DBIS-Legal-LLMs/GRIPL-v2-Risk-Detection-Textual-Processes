package de.mertendieckmann.griplbackend.evaluation.service

import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.dto.EvaluationRequest
import de.mertendieckmann.griplbackend.model.dto.ExpectedValue
import kotlin.jvm.Throws

/**
 * Result of evaluating a single BPMN test case against the analysis service.
 *
 * The full [AnalysisResponse] is preserved so downstream consumers (e.g. the
 * Ragas evaluator) can access per-element retrieval contexts and explanations
 * without making a second analysis call.
 */
data class EvaluationCallResult(
    val expectedValues: List<ExpectedValue>,
    val amountOfRetries: Int?,
    val analysisResponse: AnalysisResponse
)

interface Evaluator {

    /**
     * Analyzes a test case with the analysis matching the request's modality and endpoint
     * ([de.mertendieckmann.griplbackend.evaluation.analysisEndpointFor]): BPMN sends [bpmnXml], TEXT [processText],
     * the combined modalities both.
     */
    @Throws(Exception::class)
    suspend fun evaluate(bpmnXml: String?, processText: String?, evaluationRequest: EvaluationRequest): EvaluationCallResult
}
