package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.evaluation.EvaluationRunner
import de.mertendieckmann.griplbackend.evaluation.MultiEvaluationRunner
import de.mertendieckmann.griplbackend.evaluation.analysisEndpointFor
import de.mertendieckmann.griplbackend.evaluation.service.EvaluationCallResult
import de.mertendieckmann.griplbackend.evaluation.service.Evaluator
import de.mertendieckmann.griplbackend.evaluation.service.RagasEvaluationService
import de.mertendieckmann.griplbackend.model.dto.*
import de.mertendieckmann.griplbackend.model.dto.EvaluationModality.*
import de.mertendieckmann.griplbackend.repository.DatasetRepository
import de.mertendieckmann.griplbackend.repository.EvaluationDataRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

/** The combined modalities BPMN+TEXT (model-centric) and TEXT+BPMN (text-centric) in the evaluation. */
class CombinedModalityTest {

    private val bpmnXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="test">
          <process id="Process_1">
            <task id="Task_1" name="Record patient name" />
            <task id="Task_2" name="Archive case" />
          </process>
        </definitions>
    """.trimIndent()
    private val label = TextLabel(id = "l1", start = 0, end = 4, text = "Text", isCritical = true)
    private val paired = EvaluationData(id = 1, name = "paired", bpmnXml = bpmnXml, datasetId = 1,
        expectedValues = listOf(ExpectedValue("Task_1", "name")), processText = "Text of the process", textLabels = listOf(label))
    private val bpmnOnly = paired.copy(id = 2, processText = null, textLabels = emptyList())
    private val unlabeledText = paired.copy(id = 3, textLabels = emptyList())

    @Test
    fun `endpoints are derived per modality from the model endpoint`() {
        val pe = "/gdpr/analysis/prompt-engineering"
        val baseline = "http://localhost:8081/gdpr/analysis/baseline"
        assertEquals(pe, analysisEndpointFor(BPMN, pe))
        assertEquals("https://custom/analyze", analysisEndpointFor(BPMN, "https://custom/analyze"))
        assertEquals("/gdpr/analysis/text", analysisEndpointFor(TEXT, pe))
        assertEquals("/gdpr/analysis/bpmn-with-text", analysisEndpointFor(BPMN_WITH_TEXT, "$pe/"))
        assertEquals("/gdpr/analysis/text-with-bpmn", analysisEndpointFor(TEXT_WITH_BPMN, pe))
        assertEquals("http://localhost:8081/gdpr/analysis/text-baseline", analysisEndpointFor(TEXT, baseline))
        assertEquals("http://localhost:8081/gdpr/analysis/bpmn-with-text-baseline", analysisEndpointFor(BPMN_WITH_TEXT, baseline))
        assertEquals("http://localhost:8081/gdpr/analysis/text-with-bpmn-baseline", analysisEndpointFor(TEXT_WITH_BPMN, baseline))
        val error = assertThrows<IllegalArgumentException> { analysisEndpointFor(BPMN_WITH_TEXT, "https://custom/analyze") }
        assertTrue(error.message!!.contains("BPMN+TEXT"))
    }

    @Test
    fun `combined modalities need both parts`() {
        assertTrue(BPMN_WITH_TEXT.isAvailableFor(paired))
        assertTrue(BPMN_WITH_TEXT.isAvailableFor(unlabeledText), "scored on BPMN labels, text labels are not needed")
        assertFalse(BPMN_WITH_TEXT.isAvailableFor(bpmnOnly))
        assertTrue(TEXT_WITH_BPMN.isAvailableFor(paired))
        assertFalse(TEXT_WITH_BPMN.isAvailableFor(unlabeledText), "scored on text labels, they are needed")
        assertFalse(TEXT_WITH_BPMN.isAvailableFor(paired.copy(bpmnXml = null)))
        assertEquals(listOf(TEXT, TEXT_WITH_BPMN), EvaluationModality.entries.filter { it.scoredOnText })
    }

    private class RecordingEvaluator(val criticalIds: List<String>) : Evaluator {
        val calls = mutableListOf<Pair<String?, String?>>()
        override suspend fun evaluate(bpmnXml: String?, processText: String?, evaluationRequest: EvaluationRequest): EvaluationCallResult {
            calls += bpmnXml to processText
            val response = AnalysisResponse(criticalIds.map { AnalysisResponse.CriticalElement(id = it, name = it, reason = "r") })
            return EvaluationCallResult(criticalIds.map { ExpectedValue(it, "r") }, 0, response)
        }
    }

    @Test
    fun `BPMN+TEXT sends model and text and is scored like BPMN`() {
        val evaluator = RecordingEvaluator(listOf("Task_1", "Task_2"))
        val repository = mock(EvaluationDataRepository::class.java) { invocation ->
            if (invocation.method.name.startsWith("getEvaluationData")) listOf(paired, bpmnOnly) else null
        }
        val runner = EvaluationRunner(repository, evaluator, mock(RagasEvaluationService::class.java))
        val reports = runBlocking {
            runner.run(EvaluationRequest(datasets = listOf(1), evaluateRag = false, activitiesOnly = true, modality = BPMN_WITH_TEXT)).toList()
        }

        // the BPMN-only test case is skipped, the paired one gets model and text
        assertEquals(listOf<Pair<String?, String?>>(bpmnXml to "Text of the process"), evaluator.calls)
        val report = reports.filterIsInstance<TestCaseReport>().single()
        assertNotNull(report.imageSrc)
        assertNull(report.textResult)
        assertEquals(listOf("Task_1"), report.correctActivityIds)
        assertEquals(listOf("Task_2"), report.falsePositiveIds)
    }

    @Test
    fun `combined runs are labeled and rejected for custom endpoints before the run starts`() {
        val requests = mutableListOf<EvaluationRequest>()
        val singleRunner = mock(EvaluationRunner::class.java) { invocation ->
            if (invocation.method.name == "run") {
                requests += invocation.arguments[0] as EvaluationRequest
                kotlinx.coroutines.flow.flowOf<EvaluationReport>()
            } else null
        }
        val repository = mock(EvaluationDataRepository::class.java) { invocation ->
            if (invocation.method.name.startsWith("getEvaluationData")) listOf(paired) else null
        }
        val runner = MultiEvaluationRunner(singleRunner, mock(DatasetRepository::class.java) { emptyList<Dataset>() }, repository)
        val request = MultiEvaluationRequest(defaultEvaluationEndpoint = "/gdpr/analysis/prompt-engineering",
            models = listOf(ModelRunConfig("M")), seed = 1, datasets = listOf(1), pairedOnly = true, activitiesOnly = true,
            modalities = listOf(BPMN, TEXT, BPMN_WITH_TEXT, TEXT_WITH_BPMN))

        val metadata = runBlocking { runner.runAll(request).toList() }.first().report as EvaluationMetadataReport
        assertEquals(listOf("M [BPMN]", "M [TEXT]", "M [BPMN+TEXT]", "M [TEXT+BPMN]"), metadata.modelLabels)
        assertEquals(listOf(BPMN, TEXT, BPMN_WITH_TEXT, TEXT_WITH_BPMN), requests.map { it.modality })

        val custom = request.copy(models = listOf(ModelRunConfig("M", evaluationEndpoint = "https://custom/analyze")),
            modalities = listOf(BPMN, BPMN_WITH_TEXT))
        assertThrows<IllegalArgumentException> { runBlocking { runner.runAll(custom).toList() } }
    }
}
