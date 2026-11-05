package de.mertendieckmann.griplbackend.evaluation.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.mertendieckmann.griplbackend.evaluation.text.textAnalysisEndpointFor
import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.dto.EvaluationRequest
import de.mertendieckmann.griplbackend.model.dto.ExpectedValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

@Service
class HttpEvaluator(
    @Value("\${server.port:8080}") private val serverPort: Int
): Evaluator {

    private val webClient = WebClient.builder()
        .codecs { it.defaultCodecs().maxInMemorySize(32 * 1024 * 1024) }
        .build()

    override suspend fun evaluate(bpmnXml: String, evaluationRequest: EvaluationRequest): EvaluationCallResult {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("bpmnFile", ByteArrayResource(bpmnXml.toByteArray()))
            .header("Content-Disposition", "form-data; name=\"bpmnFile\"; filename=\"process.bpmn\"")
            .contentType(MediaType.APPLICATION_XML)
        addAnalysisParameters(bodyBuilder, evaluationRequest)
        bodyBuilder.part("activitiesOnly", evaluationRequest.activitiesOnly.toString())

        return post(evaluationRequest.evaluationEndpoint, bodyBuilder, "BPMN XML")
    }

    override suspend fun evaluateText(processText: String, evaluationRequest: EvaluationRequest): EvaluationCallResult {
        val bodyBuilder = MultipartBodyBuilder()
        // Plain form field: the text endpoint reads processText as form field part
        bodyBuilder.part("processText", processText)
        addAnalysisParameters(bodyBuilder, evaluationRequest)

        return post(textAnalysisEndpointFor(evaluationRequest.evaluationEndpoint), bodyBuilder, "process text")
    }

    private fun addAnalysisParameters(bodyBuilder: MultipartBodyBuilder, evaluationRequest: EvaluationRequest) {
        evaluationRequest.llmProps?.let { overrides ->
            bodyBuilder.part("llmProps", jacksonObjectMapper().writeValueAsString(overrides))
                .header("Content-Disposition", "form-data; name=\"llmProps\"")
                .contentType(MediaType.APPLICATION_JSON)
        }
        // Forward RAG parameters — the analysis endpoint defaults to false/hybrid when omitted,
        // but we send them explicitly so behaviour matches the evaluation request config.
        bodyBuilder.part("useRag", evaluationRequest.useRag.toString())
        bodyBuilder.part("ragMode", evaluationRequest.ragMode.toString())
    }

    private suspend fun post(endpoint: String, bodyBuilder: MultipartBodyBuilder, inputName: String): EvaluationCallResult {
        val absoluteEndpoint = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint
        } else {
            "http://localhost:$serverPort$endpoint"
        }

        try {
            val analysisResponse: AnalysisResponse = webClient
                .post()
                .uri(absoluteEndpoint)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .awaitBody()

            return EvaluationCallResult(
                expectedValues = analysisResponse.criticalElements.map {
                    ExpectedValue(value = it.id, reason = it.reason)
                },
                amountOfRetries = analysisResponse.amountOfRetries,
                analysisResponse = analysisResponse
            )
        } catch (e: WebClientResponseException) {
            throw RuntimeException("Failed to evaluate $inputName at endpoint '$absoluteEndpoint': ${e.responseBodyAsString}", e)
        }
    }
}
