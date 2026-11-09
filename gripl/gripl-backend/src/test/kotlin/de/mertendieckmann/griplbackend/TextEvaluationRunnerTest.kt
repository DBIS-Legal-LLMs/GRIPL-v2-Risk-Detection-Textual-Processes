package de.mertendieckmann.griplbackend

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.mertendieckmann.griplbackend.evaluation.EvaluationRunner
import de.mertendieckmann.griplbackend.evaluation.service.EvaluationCallResult
import de.mertendieckmann.griplbackend.evaluation.service.Evaluator
import de.mertendieckmann.griplbackend.evaluation.service.RagasEvaluationService
import de.mertendieckmann.griplbackend.evaluation.text.FalseNegativeCause
import de.mertendieckmann.griplbackend.model.dto.*
import de.mertendieckmann.griplbackend.repository.EvaluationDataRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** The text modality of the EvaluationRunner end-to-end, with a fake evaluator instead of an LLM. */
class TextEvaluationRunnerTest {

    // Worked example of the documentation
    private val text = "Der Kunde füllt das Formular aus. Ein Mitarbeiter prüft die Identität und speichert die " +
        "Daten im CRM. Danach wird eine Bestätigung versendet."

    private fun label(id: String, passage: String, critical: Boolean): TextLabel {
        val start = text.indexOf(passage)
        return TextLabel(id = id, start = start, end = start + passage.length, text = passage, isCritical = critical)
    }

    private val testCase = EvaluationData(
        id = 9, name = "Kunde", bpmnXml = null, expectedValues = emptyList(), datasetId = 1, processText = text,
        textLabels = listOf(
            label("g1", "Der Kunde füllt das Formular aus", true),
            label("g2", "Ein Mitarbeiter prüft die Identität", true),
            label("g3", "speichert die Daten im CRM", true),
            label("g4", "wird eine Bestätigung versendet", false),
        )
    )

    private fun response(p3Critical: Boolean): AnalysisResponse {
        val extracted = listOf(
            AnalysisResponse.ExtractedActivity("p1", "Formular ausfüllen", "Der Kunde füllt das Formular aus."),
            AnalysisResponse.ExtractedActivity("p2", "Identität prüfen", "Ein Mitarbeiter prüft die Identität und speichert die Daten im CRM."),
            // paraphrased, not locatable
            AnalysisResponse.ExtractedActivity("p3", "Bestätigung", "Die Bestätigung wird verschickt"),
        )
        val critical = extracted.filter { it.id != "p3" || p3Critical }
            .map { AnalysisResponse.CriticalElement(id = it.id, name = it.name, type = "task", reason = "reason ${it.id}", exactText = it.exactText) }
        return AnalysisResponse(criticalElements = critical, amountOfRetries = 0, extractedActivities = extracted)
    }

    private class FakeEvaluator(val response: AnalysisResponse) : Evaluator {
        val texts = mutableListOf<String>()
        val models = mutableListOf<String?>()
        override suspend fun evaluate(bpmnXml: String?, processText: String?, evaluationRequest: EvaluationRequest): EvaluationCallResult {
            texts += requireNotNull(processText) { "A text run must send the process text" }
            models += bpmnXml
            return EvaluationCallResult(response.criticalElements.map { ExpectedValue(it.id, it.reason) }, 0, response)
        }
    }

    private fun run(
        response: AnalysisResponse,
        testCases: List<EvaluationData> = listOf(testCase),
        modality: EvaluationModality = EvaluationModality.TEXT
    ): Pair<FakeEvaluator, List<EvaluationReport>> {
        val evaluator = FakeEvaluator(response)
        val repository = mock(EvaluationDataRepository::class.java) { invocation ->
            if (invocation.method.name.startsWith("getEvaluationData")) testCases else null
        }
        val runner = EvaluationRunner(repository, evaluator, mock(RagasEvaluationService::class.java))
        val request = EvaluationRequest(datasets = listOf(1), evaluateRag = false, modality = modality)
        return evaluator to runBlocking { runner.run(request).toList() }
    }

    @Test
    fun `scores the text test case and reports partial and strict results`() {
        val (evaluator, reports) = run(response(p3Critical = false))
        assertEquals(listOf(text), evaluator.texts)
        assertEquals(listOf<String?>(null), evaluator.models)

        val report = reports.filterIsInstance<TestCaseReport>().single()
        assertNull(report.imageSrc)
        assertEquals(listOf("g1", "g2"), report.correctActivityIds)
        assertEquals(listOf("g3"), report.falseNegativeIds)
        assertEquals(emptyList<String>(), report.falsePositiveIds)
        assertFalse(report.isSuccessful)
        assertEquals(mapOf("activity" to 2), report.perElementType.mapValues { it.value.truePositives })

        val result = report.textResult!!
        assertEquals(
            listOf(TextLabelOutcome.TP, TextLabelOutcome.TP, TextLabelOutcome.FN, TextLabelOutcome.TN),
            result.labels.map { it.outcome }
        )
        assertEquals(FalseNegativeCause.EXTRACTION, result.labels[2].falseNegativeCause)
        assertEquals(listOf("g1", "g2", null), result.predictions.map { it.matchedLabelId })
        assertNull(result.predictions[2].start)
        assertEquals(2.0 / 3, result.extractionPrecision, 1e-9)
        assertEquals(0.5, result.extractionRecall, 1e-9)
        assertEquals(1, result.strict.falseNegatives)

        val summary = reports.filterIsInstance<EvaluationReportSummary>().single()
        assertEquals(listOf(2, 0, 1, 1), listOf(summary.totalTruePositives, summary.totalFalsePositives,
            summary.totalFalseNegatives, summary.totalTrueNegatives))
        val textMetrics = summary.textMetrics!!
        assertEquals(1, textMetrics.evaluatedTestCases)
        assertEquals(2.0 / 3, textMetrics.extractionPrecision, 1e-9)
        assertEquals(2.0 / 3, textMetrics.localizationRate, 1e-9)
        assertEquals((32.0 / 33 + 35.0 / 67) / 2, textMetrics.meanIou, 1e-9)
        assertEquals(1, textMetrics.falseNegativesByExtraction)
    }

    @Test
    fun `false positive ids are the predictions that caused them`() {
        val (_, reports) = run(response(p3Critical = true))
        val report = reports.filterIsInstance<TestCaseReport>().single()
        // p3 is critical but cannot be located, so it matches no label
        assertEquals(listOf("p3"), report.falsePositiveIds)
        assertTrue(report.actualNamesWithIds.any { it.contains("p3") })
    }

    @Test
    fun `test cases without labeled text are skipped`() {
        val bpmnOnly = testCase.copy(id = 10, processText = null, textLabels = emptyList(), bpmnXml = "<xml/>")
        val (evaluator, reports) = run(response(false), listOf(testCase, bpmnOnly))
        assertEquals(1, evaluator.texts.size)
        assertEquals(listOf(9L), reports.filterIsInstance<TestCaseReport>().map { it.testCaseId })
    }

    @Test
    fun `report json and markdown`() {
        val (_, reports) = run(response(false))
        val report = reports.filterIsInstance<TestCaseReport>().single()
        val json = jacksonObjectMapper().readTree(jacksonObjectMapper().writeValueAsString(report))
        assertTrue(json["textResult"]["labels"][0].has("isCritical"))
        assertFalse(report.toMarkdown().contains("null"))
        assertTrue(report.toMarkdown().contains("Text Scoring"))

        val bpmnReport = report.copy(imageSrc = "img.svg", textResult = null)
        val bpmnJson = jacksonObjectMapper().readTree(jacksonObjectMapper().writeValueAsString(bpmnReport))
        assertFalse(bpmnJson.has("textResult"))
        assertTrue(bpmnReport.toMarkdown().contains("<img src=\"img.svg\""))
    }

    @Test
    fun `TEXT+BPMN sends the model as reference and is scored like text`() {
        val paired = testCase.copy(bpmnXml = "<xml/>")
        val (evaluator, reports) = run(response(p3Critical = false), listOf(paired), EvaluationModality.TEXT_WITH_BPMN)

        assertEquals(listOf<String?>("<xml/>"), evaluator.models)
        val report = reports.filterIsInstance<TestCaseReport>().single()
        assertEquals(listOf("g1", "g2"), report.correctActivityIds)
        assertNotNull(report.textResult)
    }
}
