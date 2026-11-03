package de.mertendieckmann.griplbackend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * A test case represents one process. It holds a BPMN model, a textual process description, or both (paired),
 * each with its own labels: [expectedValues] for the BPMN model, [textLabels] for the text.
 */
data class EvaluationData(
    val id: Long,
    val name: String?,
    val bpmnXml: String?,
    val expectedValues: List<ExpectedValue>,
    val datasetId: Long? = null,
    val processText: String? = null,
    val textLabels: List<TextLabel> = emptyList()
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun fromRow(
            id: Long,
            name: String?,
            bpmnXml: String?,
            expectedJson: String,
            datasetId: Long?,
            processText: String?,
            textLabelsJson: String?
        ): EvaluationData {
            val list: List<ExpectedValue> = mapper.readValue(expectedJson)
            val textLabels: List<TextLabel> = textLabelsJson?.let { mapper.readValue(it) } ?: emptyList()
            return EvaluationData(
                id = id,
                name = name,
                bpmnXml = bpmnXml,
                expectedValues = list,
                datasetId = datasetId,
                processText = processText,
                textLabels = textLabels
            )
        }
    }
}

data class ExpectedValue(
    val value: String,
    val reason: String? = null
)

/**
 * A labeled passage of the textual process description. Every activity in the text is labeled, the GDPR-critical
 * ones are flagged with [isCritical]  so non-critical activities form the true negatives of the text modality.
 *
 * [start]/[end] are character offsets into the process text (end exclusive, UTF-16 code units — identical in
 * JavaScript and Kotlin), [text] is the labeled passage itself,
 * used to re-anchor the label if the text is edited afterward.
 */
data class TextLabel(
    val id: String,
    val start: Int,
    val end: Int,
    val text: String,
    // Explicit name, so Jackson does not strip the "is" prefix
    @param:JsonProperty("isCritical") @get:JsonProperty("isCritical")
    val isCritical: Boolean = false,
    val reason: String? = null
)

/**
 * The textual process description of a test case with its labels, sent as one JSON part — unlike a plain text part
 * it is never empty (so an empty text can be saved) and keeps line breaks exactly (so label offsets stay valid).
 */
data class TextDescription(
    val processText: String,
    val textLabels: List<TextLabel> = emptyList()
)

data class EvaluationDataMeta(
    val id: Long,
    val name: String?,
    val datasetId: Long?,
    val hasBpmn: Boolean = true,
    val hasText: Boolean = false
)

data class EvaluationDataWithOptionalId(
    val id: Long? = null,
    val name: String? = null,
    val bpmnXml: String?,
    val expectedValues: List<ExpectedValue>,
    val datasetId: Long? = null,
    val processText: String? = null,
    val textLabels: List<TextLabel> = emptyList()
)
