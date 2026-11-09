package de.mertendieckmann.griplbackend.adapter.web

import de.mertendieckmann.griplbackend.adapter.web.utils.ControllerUtils
import de.mertendieckmann.griplbackend.application.analyzer.AnalyzerFactory
import de.mertendieckmann.griplbackend.application.analyzer.BpmnAnalyzer
import de.mertendieckmann.griplbackend.config.LlmConfig
import de.mertendieckmann.griplbackend.model.dto.AnalysisEndpoint
import de.mertendieckmann.griplbackend.model.dto.AnalysisResponse
import de.mertendieckmann.griplbackend.model.dto.RagMode
import dev.langchain4j.model.chat.ChatModel
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/gdpr/analysis")
class AnalysisController(
    private val analyzerFactory: AnalyzerFactory,
    private val llmConfig: LlmConfig,
    @Qualifier("analysisEndpoints") private val analysisEndpoints: List<AnalysisEndpoint>,
    private val env: Environment
) {

    @Operation(
        summary = "Get all available analysis endpoints",
        description = "Returns a list of all available analysis endpoints that can be used for GDPR analysis."
    )
    @GetMapping("/endpoints", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAnalysisEndpoints(): ResponseEntity<List<AnalysisEndpoint>> {
        return ResponseEntity(analysisEndpoints, HttpStatus.OK)
    }

    @Operation(
        summary = "Analyzes BPMN-XML for GDPR relevance with prompt engineering",
        description = "Upload a BPMN XML document (file part **bpmnFile**). The service analyzes it with an LLM, and returns a list"
            + " of GDPR-relevant elements found in the BPMN model, including the reasoning for each element."
    )
    @PostMapping(
        "/prompt-engineering",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeBpmnForGdprPromptEngineering(
        @RequestPart("bpmnFile") file: FilePart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("activitiesOnly", required = false) activitiesOnlyPart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> {

        val useRag = useRagPart?.value()?.toBooleanStrictOrNull() ?: false
        val ragMode = parseRagMode(ragModePart)
        val activitiesOnly = activitiesOnlyPart?.value()?.toBooleanStrictOrNull() ?: false

        val bpmnXmlMono: Mono<String> = ControllerUtils.getBpmnXmlMono(file)
        val resolvedLlmPropsOverride = ControllerUtils.resolveEnvironmentVariables(llmPropsOverrides, env)

        return bpmnXmlMono.flatMap { bpmnXml ->
            Mono.fromCallable {
                val llm = llmConfig.buildStrictJsonModelWithOverride(resolvedLlmPropsOverride)
                val analyzer = analyzerFactory.createPromptEngineeringAnalyzer(llm)
                analyzer.analyzeBpmnForGdpr(
                    bpmnXml = bpmnXml,
                    useRag = useRag,
                    ragMode = ragMode,
                    activitiesOnly = activitiesOnly
                )            }.subscribeOn(Schedulers.boundedElastic())
        }.map { ResponseEntity.ok(it) }
    }

    @Operation(
        summary = "Analyzes BPMN-XML for GDPR relevance using baseline method",
        description = "Upload a BPMN XML document (file part **bpmnFile**). The service analyzes it with a baseline method, and returns a list"
            + " of GDPR-relevant elements found in the BPMN model, including the reasoning for each element."
    )
    @PostMapping(
        "/baseline",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeBpmnForGdprBaseline(
        @RequestPart("bpmnFile") file: FilePart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("activitiesOnly", required = false) activitiesOnlyPart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> {

        val useRag = useRagPart?.value()?.toBooleanStrictOrNull() ?: false
        val ragMode = parseRagMode(ragModePart)
        val activitiesOnly = activitiesOnlyPart?.value()?.toBooleanStrictOrNull() ?: false
        val bpmnXmlMono: Mono<String> = ControllerUtils.getBpmnXmlMono(file)
        val resolvedLlmPropsOverride = ControllerUtils.resolveEnvironmentVariables(llmPropsOverrides, env)

        return bpmnXmlMono.flatMap { bpmnXml ->
            Mono.fromCallable {
                val llm = llmConfig.buildStrictJsonModelWithOverride(resolvedLlmPropsOverride)
                val analyzer = analyzerFactory.createBaselineAnalyzer(llm)
                analyzer.analyzeBpmnForGdpr(
                    bpmnXml = bpmnXml,
                    useRag = useRag,
                    ragMode = ragMode,
                    activitiesOnly = activitiesOnly
                )            }.subscribeOn(Schedulers.boundedElastic())
        }.map { ResponseEntity.ok(it) }
    }

    @Operation(
        summary = "(Combi Analysis BPMN+TEXT) Analyzes BPMN-XML for GDPR relevance with prompt engineering, using the textual description as context",
        description = "Model-centric combined analysis: upload the BPMN model (file part **bpmnFile**) and the textual description"
            + " of the same process (form field **processText**). The model is analyzed like /prompt-engineering, with the"
            + " description appended as additional context; the BPMN model defines which elements are classified."
    )
    @PostMapping(
        "/bpmn-with-text",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeBpmnWithTextForGdprPromptEngineering(
        @RequestPart("bpmnFile") file: FilePart,
        @RequestPart("processText") processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("activitiesOnly", required = false) activitiesOnlyPart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> =
        analyzeBpmnWithText(file, processTextPart, llmPropsOverrides, useRagPart, ragModePart, activitiesOnlyPart) {
            analyzerFactory.createPromptEngineeringAnalyzer(it)
        }

    @Operation(
        summary = "Analyzes BPMN-XML for GDPR relevance using baseline method, with the textual description as context",
        description = "Baseline counterpart of /bpmn-with-text: the BPMN model (file part **bpmnFile**) is analyzed like /baseline,"
            + " with the textual description (form field **processText**) appended as additional context."
    )
    @PostMapping(
        "/bpmn-with-text-baseline",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeBpmnWithTextForGdprBaseline(
        @RequestPart("bpmnFile") file: FilePart,
        @RequestPart("processText") processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("activitiesOnly", required = false) activitiesOnlyPart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> =
        analyzeBpmnWithText(file, processTextPart, llmPropsOverrides, useRagPart, ragModePart, activitiesOnlyPart) {
            analyzerFactory.createBaselineAnalyzer(it)
        }

    // Shared by both model-centric combined endpoints
    private fun analyzeBpmnWithText(
        file: FilePart,
        processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride?,
        useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        activitiesOnlyPart: org.springframework.http.codec.multipart.FormFieldPart?,
        analyzerOf: (ChatModel) -> BpmnAnalyzer
    ): Mono<ResponseEntity<AnalysisResponse>> {
        val processText = processTextPart.value()
        if (processText.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "processText must not be empty")
        }
        val useRag = useRagPart?.value()?.toBooleanStrictOrNull() ?: false
        val ragMode = parseRagMode(ragModePart)
        val activitiesOnly = activitiesOnlyPart?.value()?.toBooleanStrictOrNull() ?: false
        val resolvedLlmPropsOverride = ControllerUtils.resolveEnvironmentVariables(llmPropsOverrides, env)

        return ControllerUtils.getBpmnXmlMono(file).flatMap { bpmnXml ->
            Mono.fromCallable {
                val llm = llmConfig.buildStrictJsonModelWithOverride(resolvedLlmPropsOverride)
                analyzerOf(llm).analyzeBpmnWithTextForGdpr(bpmnXml, processText, useRag, ragMode, activitiesOnly)
            }.subscribeOn(Schedulers.boundedElastic())
        }.map { ResponseEntity.ok(it) }
    }

    @Operation(
        summary = "Analyzes a textual process description for GDPR relevance (two-step pipeline)",
        description = "Send the process description as form field **processText**. Step 1 extracts the activities from the text,"
            + " step 2 classifies them with the prompt-engineering pipeline (activities only). Returns the GDPR-relevant"
            + " activities with their synthetic IDs (activity-1, activity-2, ...) and the reasoning for each activity."
    )
    @PostMapping(
        "/text",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeTextForGdpr(
        @RequestPart("processText") processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> =
        analyzeText(processTextPart, llmPropsOverrides, useRagPart, ragModePart) { llm, processText, useRag, ragMode ->
            analyzerFactory.createTextAnalyzer(llm).analyzeTextForGdpr(processText, useRag, ragMode)
        }

    @Operation(
        summary = "Analyzes a textual process description for GDPR relevance using baseline method",
        description = "Send the process description as form field **processText**. A single LLM call with a minimal prompt"
            + " extracts the activities and classifies them at once (no separate extraction step, no RAG). Returns the"
            + " same response shape as /text: the GDPR-relevant activities and all extracted activities."
    )
    @PostMapping(
        "/text-baseline",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun analyzeTextForGdprBaseline(
        @RequestPart("processText") processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        @RequestPart("llmProps", required = false) llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride? = null,
        @RequestPart("useRag", required = false) useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        @RequestPart("ragMode", required = false) ragModePart: org.springframework.http.codec.multipart.FormFieldPart?
    ): Mono<ResponseEntity<AnalysisResponse>> =
        analyzeText(processTextPart, llmPropsOverrides, useRagPart, ragModePart) { llm, processText, useRag, _ ->
            analyzerFactory.createBaselineTextAnalyzer(llm).analyzeTextForGdpr(processText, useRag)
        }

    // Shared by both text endpoints: validates the text, reads the parameters and builds the LLM per request
    private fun analyzeText(
        processTextPart: org.springframework.http.codec.multipart.FormFieldPart,
        llmPropsOverrides: LlmConfig.Companion.LlmPropsOverride?,
        useRagPart: org.springframework.http.codec.multipart.FormFieldPart?,
        ragModePart: org.springframework.http.codec.multipart.FormFieldPart?,
        analyze: (llm: ChatModel, processText: String, useRag: Boolean, ragMode: RagMode) -> AnalysisResponse
    ): Mono<ResponseEntity<AnalysisResponse>> {
        val processText = processTextPart.value()
        if (processText.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "processText must not be empty")
        }
        val useRag = useRagPart?.value()?.toBooleanStrictOrNull() ?: false
        val ragMode = parseRagMode(ragModePart)
        val resolvedLlmPropsOverride = ControllerUtils.resolveEnvironmentVariables(llmPropsOverrides, env)

        return Mono.fromCallable {
            val llm = llmConfig.buildStrictJsonModelWithOverride(resolvedLlmPropsOverride)
            analyze(llm, processText, useRag, ragMode)
        }.subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }
    }

    private fun parseRagMode(ragModePart: org.springframework.http.codec.multipart.FormFieldPart?): RagMode {
        val raw = ragModePart?.value() ?: return RagMode.HYBRID
        return try {
            RagMode.fromString(raw)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }
}