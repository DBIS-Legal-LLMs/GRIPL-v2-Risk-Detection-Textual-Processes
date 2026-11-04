package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.evaluation.EvaluationRunner
import de.mertendieckmann.griplbackend.evaluation.MultiEvaluationRunner
import de.mertendieckmann.griplbackend.evaluation.selectTestCases
import de.mertendieckmann.griplbackend.model.dto.*
import de.mertendieckmann.griplbackend.model.dto.EvaluationModality.BPMN
import de.mertendieckmann.griplbackend.model.dto.EvaluationModality.TEXT
import de.mertendieckmann.griplbackend.repository.DatasetRepository
import de.mertendieckmann.griplbackend.repository.EvaluationDataRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class EvaluationModalityTest {

    private val label = TextLabel(id = "l1", start = 0, end = 4, text = "Text", isCritical = true)
    private val bpmnOnly = EvaluationData(id = 1, name = "bpmn", bpmnXml = "<xml/>", expectedValues = emptyList())
    private val textOnly = EvaluationData(id = 2, name = "text", bpmnXml = null, expectedValues = emptyList(),
        processText = "Text", textLabels = listOf(label))
    private val paired = bpmnOnly.copy(id = 3, name = "paired", processText = "Text", textLabels = listOf(label))
    private val unlabeledText = textOnly.copy(id = 4, textLabels = emptyList())
    private val testCases = listOf(bpmnOnly, textOnly, paired, unlabeledText)

    @Test
    fun `test cases are selected by the modalities they provide`() {
        assertEquals(listOf(1L, 3L), selectTestCases(testCases, BPMN).map { it.id })
        // a text without labels has no ground truth
        assertEquals(listOf(2L, 3L), selectTestCases(testCases, TEXT).map { it.id })
        // paired: only test cases providing every modality of the run
        assertEquals(listOf(3L), selectTestCases(testCases, BPMN, setOf(BPMN, TEXT)).map { it.id })
    }

    /** MultiEvaluationRunner with a fake EvaluationRunner that records the requests it receives. */
    private inner class Fixture {
        val requests = mutableListOf<EvaluationRequest>()
        private val step = EvaluationReportStepInfo("tc", 1, 1, 1)
        private val singleRunner = mock(EvaluationRunner::class.java) { invocation ->
            if (invocation.method.name == "run") {
                requests += invocation.arguments[0] as EvaluationRequest
                flowOf<EvaluationReport>(step)
            } else null
        }
        private val datasetRepository = mock(DatasetRepository::class.java) { emptyList<Dataset>() }
        private val evaluationDataRepository = mock(EvaluationDataRepository::class.java) { invocation ->
            if (invocation.method.name.startsWith("getEvaluationData")) testCases else null
        }
        val runner = MultiEvaluationRunner(singleRunner, datasetRepository, evaluationDataRepository)

        fun run(request: MultiEvaluationRequest) = runBlocking { runner.runAll(request).toList() }
    }

    private fun request(modalities: List<EvaluationModality>? = null, pairedOnly: Boolean = false) =
        if (modalities == null) {
            MultiEvaluationRequest(defaultEvaluationEndpoint = "/e", models = listOf(ModelRunConfig("A"), ModelRunConfig("B")),
                seed = 7, datasets = listOf(1), pairedOnly = pairedOnly)
        } else {
            MultiEvaluationRequest(defaultEvaluationEndpoint = "/e", models = listOf(ModelRunConfig("A"), ModelRunConfig("B")),
                seed = 7, datasets = listOf(1), modalities = modalities, pairedOnly = pairedOnly)
        }

    @Test
    fun `default run is BPMN only and unchanged`() {
        val fixture = Fixture()
        val envelopes = fixture.run(request())

        val metadata = envelopes.first().report as EvaluationMetadataReport
        assertEquals(listOf("A", "B"), metadata.modelLabels)
        assertEquals(2, metadata.totalTestCases)
        assertEquals(mapOf(BPMN to 2), metadata.totalTestCasesByModality)
        assertFalse(metadata.toMarkdown().contains("Modalities"))

        // model labels without a modality suffix, every report tagged as BPMN
        assertEquals(listOf("A", "B"), envelopes.drop(1).map { it.modelLabel })
        assertTrue(envelopes.all { it.modality == BPMN })
        assertEquals(listOf(BPMN, BPMN), fixture.requests.map { it.modality })
        assertTrue(fixture.requests.all { it.requiredModalities.isEmpty() })
    }

    @Test
    fun `pairedOnly with a single modality selects the same test cases`() {
        val fixture = Fixture()
        val metadata = fixture.run(request(pairedOnly = true)).first().report as EvaluationMetadataReport
        assertEquals(2, metadata.totalTestCases)
        assertEquals(setOf(BPMN), fixture.requests.first().requiredModalities)
    }

    @Test
    fun `text modality is rejected until it is wired in`() {
        val error = assertThrows<IllegalArgumentException> { Fixture().run(request(listOf(BPMN, TEXT))) }
        assertTrue(error.message!!.contains("TEXT"))
    }
}
