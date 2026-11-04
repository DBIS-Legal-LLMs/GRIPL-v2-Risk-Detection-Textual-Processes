package de.mertendieckmann.griplbackend.model.dto

import de.mertendieckmann.griplbackend.config.LlmConfig

data class MultiEvaluationRequest(
    val defaultEvaluationEndpoint: String,
    val maxConcurrent: Int = 4,
    val models: List<ModelRunConfig>,
    val seed: Int?,
    val datasets: List<Int>,
    val evaluationDataIds: List<Int> = emptyList(),
    val repetitions: Int = 1,
    val useRag: Boolean = false,
    val ragMode: RagMode = RagMode.HYBRID,
    val evaluateRag: Boolean = true,
    val activitiesOnly: Boolean = false,
    /** Modalities every model is evaluated with; more than one adds the modality to the model label. */
    val modalities: List<EvaluationModality> = listOf(EvaluationModality.BPMN),
    /** Only evaluate test cases that provide all [modalities], so the modalities are compared on the same processes. */
    val pairedOnly: Boolean = false,
)

data class ModelRunConfig(
    val label: String,
    val evaluationEndpoint: String? = null,
    val llmProps: LlmConfig.Companion.LlmPropsOverride? = null,
) {
    override fun toString(): String =
        "EvaluationRequest(label=$label, llmProps=${llmProps?.copy(apiKey = llmProps.apiKey?.let { "\"****\"" })})"
}
