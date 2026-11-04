package de.mertendieckmann.griplbackend.repository

import com.fasterxml.jackson.databind.ObjectMapper
import de.mertendieckmann.griplbackend.model.dto.Dataset
import de.mertendieckmann.griplbackend.model.dto.EvaluationData
import de.mertendieckmann.griplbackend.model.dto.EvaluationDataWithOptionalId
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class EvaluationDataRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    private val mapper = RowMapper { rs, _ ->
        EvaluationData.fromRow(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            bpmnXml = rs.getString("bpmn_xml"),
            expectedJson = rs.getString("expected_values"),
            datasetId = rs.getLong("dataset_id"),
            processText = rs.getString("process_text"),
            textLabelsJson = rs.getString("text_labels"),
        )
    }

    fun getAllEvaluationData(): List<EvaluationData> {
        return jdbc.query("SELECT * FROM evaluation_data", mapper)
    }

    fun getEvaluationDataByDatasetIdsOrAll(dataset: List<Int>): List<EvaluationData> {
        if (dataset.isEmpty()) {
            return getAllEvaluationData()
        }
        val inSql = dataset.joinToString(",")
        return jdbc.query("SELECT * FROM evaluation_data WHERE dataset_id IN ($inSql)", mapper)
    }

    fun getEvaluationDataByIds(ids: List<Int>): List<EvaluationData> {
        if (ids.isEmpty()) return emptyList()
        val inSql = ids.joinToString(",")
        return jdbc.query("SELECT * FROM evaluation_data WHERE id IN ($inSql)", mapper)
    }

    fun getEvaluationDataById(id: Long): EvaluationData? {
        return jdbc.query("SELECT * FROM evaluation_data WHERE id = ?", mapper, id).firstOrNull()
    }

    fun insertEvaluationData(data: EvaluationDataWithOptionalId): Int? {
        val sql = """
            INSERT INTO evaluation_data (name, bpmn_xml, expected_values, dataset_id, process_text, text_labels)
            VALUES (?, ?, ?::jsonb, ?, ?, ?::jsonb)
            RETURNING id
        """.trimIndent()

        return jdbc.queryForObject(
            sql,
            Int::class.java,
            data.name,
            data.bpmnXml,
            toJsonb(data.expectedValues),
            data.datasetId,
            data.processText,
            toJsonb(data.textLabels)
        )!!
    }

    fun updateEvaluationData(data: EvaluationData): Int {
        val sql = """
            UPDATE evaluation_data
            SET name = ?, bpmn_xml = ?, expected_values = ?::jsonb, process_text = ?, text_labels = ?::jsonb,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """.trimIndent()

        return jdbc.update(sql) { ps ->
            ps.setString(1, data.name)
            ps.setString(2, data.bpmnXml)
            ps.setObject(3, toJsonb(data.expectedValues))
            ps.setString(4, data.processText)
            ps.setObject(5, toJsonb(data.textLabels))
            ps.setLong(6, data.id)
        }
    }

    private fun toJsonb(value: Any): PGobject =
        PGobject().apply { type = "jsonb"; this.value = objectMapper.writeValueAsString(value) }

    fun deleteEvaluationData(id: Long): Int {
        val sql = "DELETE FROM evaluation_data WHERE id = ?"
        return jdbc.update(sql, id)
    }
}