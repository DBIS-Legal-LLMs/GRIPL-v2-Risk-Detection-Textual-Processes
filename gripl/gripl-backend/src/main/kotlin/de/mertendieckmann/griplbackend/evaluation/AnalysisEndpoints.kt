package de.mertendieckmann.griplbackend.evaluation

import de.mertendieckmann.griplbackend.model.dto.EvaluationModality

const val PROMPT_ENGINEERING_ENDPOINT = "/gdpr/analysis/prompt-engineering"
const val BASELINE_ENDPOINT = "/gdpr/analysis/baseline"

// For every non-BPMN modality: each BPMN analysis approach (GRIPL, baseline) and its counterpart
private val MODALITY_ENDPOINTS: Map<EvaluationModality, Map<String, String>> = mapOf(
    EvaluationModality.TEXT to mapOf(
        PROMPT_ENGINEERING_ENDPOINT to "/gdpr/analysis/text",
        BASELINE_ENDPOINT to "/gdpr/analysis/text-baseline",
    ),
    EvaluationModality.BPMN_WITH_TEXT to mapOf(
        PROMPT_ENGINEERING_ENDPOINT to "/gdpr/analysis/bpmn-with-text",
        BASELINE_ENDPOINT to "/gdpr/analysis/bpmn-with-text-baseline",
    ),
    EvaluationModality.TEXT_WITH_BPMN to mapOf(
        PROMPT_ENGINEERING_ENDPOINT to "/gdpr/analysis/text-with-bpmn",
        BASELINE_ENDPOINT to "/gdpr/analysis/text-with-bpmn-baseline",
    ),
)

/**
 * The analysis endpoint for [modality] of a model whose (BPMN) endpoint is [bpmnEndpoint]. BPMN uses the endpoint as
 * is; every other modality maps the prompt-engineering analysis and the baseline to their counterpart (relative or
 * absolute, keeping host and port). Custom endpoints are rejected for them, as their counterpart is unknown.
 */
fun analysisEndpointFor(modality: EvaluationModality, bpmnEndpoint: String): String {
    val counterparts = MODALITY_ENDPOINTS[modality] ?: return bpmnEndpoint
    val endpoint = bpmnEndpoint.trimEnd('/')
    val (bpmnPath, modalityPath) = requireNotNull(counterparts.entries.firstOrNull { endpoint.endsWith(it.key) }) {
        "The ${modality.label} modality is only available for the prompt-engineering analysis " +
            "($PROMPT_ENGINEERING_ENDPOINT) and the baseline ($BASELINE_ENDPOINT), not for '$bpmnEndpoint'"
    }
    return endpoint.removeSuffix(bpmnPath) + modalityPath
}
