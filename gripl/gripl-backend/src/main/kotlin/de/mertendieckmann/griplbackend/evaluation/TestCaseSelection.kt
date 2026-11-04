package de.mertendieckmann.griplbackend.evaluation

import de.mertendieckmann.griplbackend.model.dto.EvaluationData
import de.mertendieckmann.griplbackend.model.dto.EvaluationModality
import de.mertendieckmann.griplbackend.repository.EvaluationDataRepository

/** Loads the test cases of a run: the explicitly selected ids, otherwise all test cases of the selected datasets. */
fun loadTestCases(repository: EvaluationDataRepository, evaluationDataIds: List<Int>, datasets: List<Int>): List<EvaluationData> =
    if (evaluationDataIds.isNotEmpty()) {
        repository.getEvaluationDataByIds(evaluationDataIds)
    } else {
        repository.getEvaluationDataByDatasetIdsOrAll(datasets)
    }

/**
 * The test cases that are evaluated with [modality]: those providing it and every modality in [requiredModalities].
 * With `pairedOnly`, [requiredModalities] holds all modalities of the run, so every modality is evaluated on exactly
 * the same processes.
 */
fun selectTestCases(
    testCases: List<EvaluationData>,
    modality: EvaluationModality,
    requiredModalities: Set<EvaluationModality> = emptySet()
): List<EvaluationData> =
    testCases.filter { testCase -> (requiredModalities + modality).all { it.isAvailableFor(testCase) } }
