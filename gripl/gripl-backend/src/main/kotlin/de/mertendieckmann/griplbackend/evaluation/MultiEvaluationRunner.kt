package de.mertendieckmann.griplbackend.evaluation

import de.mertendieckmann.griplbackend.evaluation.text.textAnalysisEndpointFor
import de.mertendieckmann.griplbackend.model.dto.EvaluationMetadataReport
import de.mertendieckmann.griplbackend.model.dto.EvaluationModality
import de.mertendieckmann.griplbackend.model.dto.EvaluationRequest
import de.mertendieckmann.griplbackend.model.dto.ModelReportEnvelope
import de.mertendieckmann.griplbackend.model.dto.MultiEvaluationRequest
import de.mertendieckmann.griplbackend.repository.DatasetRepository
import de.mertendieckmann.griplbackend.repository.EvaluationDataRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class MultiEvaluationRunner(
    private val singleRunner: EvaluationRunner,
    private val datasetRepository: DatasetRepository,
    private val evaluationDataRepository: EvaluationDataRepository
) {
    private val log = KotlinLogging.logger {}

    fun runAll(request: MultiEvaluationRequest): Flow<ModelReportEnvelope> = flow {
        require(request.models.isNotEmpty()) { "models must not be empty" }
        require(request.modalities.isNotEmpty()) { "modalities must not be empty" }

        val modalities = request.modalities.distinct()
        if (EvaluationModality.TEXT in modalities) {
            // Fail before the run starts: text counterparts exist only for the prompt-engineering analysis and the baseline
            request.models.forEach { textAnalysisEndpointFor(it.evaluationEndpoint ?: request.defaultEvaluationEndpoint) }
            if (EvaluationModality.BPMN in modalities && !request.activitiesOnly) {
                log.warn { "Comparing BPMN with TEXT without activitiesOnly: BPMN also scores events, gateways and data " +
                    "objects, while text consists of activities only - set activitiesOnly for a fair comparison" }
            }
        }
        val baseSeed = request.seed ?: (System.currentTimeMillis() * (Math.random() * 10) % Int.MAX_VALUE).toInt()
        val repetitions = request.repetitions.coerceAtLeast(1)
        emit(ModelReportEnvelope("", createMetadata(request, modalities, baseSeed, repetitions), 1))

        for (runNumber in 1..repetitions) {
            log.info { "Starting evaluation run $runNumber/$repetitions" }

            // The seed only depends on the run, so all modalities and models of a run share it
            val runSeed = deriveRunSeed(baseSeed, runNumber)

            for (modality in modalities) {
                for ((index, model) in request.models.withIndex()) {
                    val effectiveEndpoint = model.evaluationEndpoint ?: request.defaultEvaluationEndpoint
                    val label = runLabel(model.label, modality, modalities)
                    log.info { "Run $runNumber - Starting model ${index + 1}/${request.models.size}: '$label' @ $effectiveEndpoint" }

                    val singleRequest = EvaluationRequest(
                        evaluationEndpoint = effectiveEndpoint,
                        llmProps = model.llmProps?.copy(seed = runSeed),
                        maxConcurrent = request.maxConcurrent,
                        datasets = request.datasets,
                        evaluationDataIds = request.evaluationDataIds,
                        useRag = request.useRag,
                        ragMode = request.ragMode,
                        evaluateRag = request.evaluateRag,
                        activitiesOnly = request.activitiesOnly,
                        modality = modality,
                        requiredModalities = if (request.pairedOnly) modalities.toSet() else emptySet()
                    )

                    singleRunner.run(singleRequest)
                        .map { event -> ModelReportEnvelope(label, event, runNumber, modality) }
                        .collect { wrapped -> emit(wrapped) }

                    log.info { "Run $runNumber - Finished model '$label'" }
                }
            }
        }
    }

    private fun createMetadata(
        request: MultiEvaluationRequest,
        modalities: List<EvaluationModality>,
        seed: Int,
        repetitions: Int
    ): EvaluationMetadataReport {
        val datasets = datasetRepository.getDatasetsByIds(request.datasets.map { it.toLong() })

        // Same selection as the EvaluationRunner, so the counts match the test cases actually evaluated
        val testCases = loadTestCases(evaluationDataRepository, request.evaluationDataIds, request.datasets)
        val requiredModalities = if (request.pairedOnly) modalities.toSet() else emptySet()
        val selectedByModality = modalities.associateWith { selectTestCases(testCases, it, requiredModalities) }

        // One entry per evaluated label, in the order the runs are emitted
        val runs = modalities.flatMap { modality -> request.models.map { model -> modality to model } }

        return EvaluationMetadataReport(
            modelLabels = runs.map { (modality, model) -> runLabel(model.label, modality, modalities) },
            modelTemperatures = runs.map { (_, model) -> model.llmProps?.temperature },
            modelTopPs = runs.map { (_, model) -> model.llmProps?.topP },
            datasets = datasets.map { EvaluationMetadataReport.DatasetInfo(it.id, it.name) },
            totalTestCases = selectedByModality.values.flatten().map { it.id }.distinct().size,
            seed = seed,
            defaultEvaluationEndpoint = request.defaultEvaluationEndpoint,
            totalRepetitions = repetitions,
            activitiesOnly = request.activitiesOnly,
            modalities = modalities,
            pairedOnly = request.pairedOnly,
            totalTestCasesByModality = selectedByModality.mapValues { it.value.size }
        )
    }

    /** With several modalities the model label carries the modality, e.g. "GPT-4o [TEXT]"; otherwise it is unchanged. */
    private fun runLabel(modelLabel: String, modality: EvaluationModality, modalities: List<EvaluationModality>): String =
        if (modalities.size > 1) "$modelLabel [${modality.label}]" else modelLabel

    /**
     * Stable and deterministic derivation of a run-specific seed from a base seed and the run number.
     * The result is guaranteed to be in the range [1..Int.MAX_VALUE].
     */
    private fun deriveRunSeed(baseSeed: Int, runNumber: Int): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(baseSeed)
            .putInt(runNumber)
            .array()
        val hash = digest.digest(payload)
        val raw = ByteBuffer.wrap(hash, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        val position = raw and 0x7fffffff
        return if (position == 0) 1 else position
    }
}
