package de.mertendieckmann.griplbackend.adapter.cli

import de.mertendieckmann.griplbackend.application.analyzer.AnalyzerFactory
import de.mertendieckmann.griplbackend.config.LlmConfig
import de.mertendieckmann.griplbackend.model.dto.RagMode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import de.mertendieckmann.griplbackend.config.LlmConfig.Companion.LlmPropsOverride

/**
 * CLI test harness for the two-step text-analysis pipeline (extraction + GDPR classification).
 * With --extractOnly, only Step 1 (activity extraction) is run.
 */
@Command(
    name = "text-analysis",
    description = ["Analyze a textual process description for GDPR compliance (two-step: activity extraction, then classification)"],
    mixinStandardHelpOptions = true
)
@Component
class TextAnalysisCommand(
    private val analyzerFactory: AnalyzerFactory,
    private val LlmConfig: LlmConfig
): Runnable {

    private val log = KotlinLogging.logger { }

    @Parameters(paramLabel = "TEXT_FILE", description = ["Path to a plain-text file containing the process description"]) lateinit var textFilePath: Path
    @Option(names = ["-o", "--outputFormat"], defaultValue = "pretty", description = [
        "Output format for the extraction result. Supported formats: 'pretty' (default), 'json'."
    ]) lateinit var outputFormat: String
    @Option(names = ["--baseUrl"], description = ["Base URL for the LLM API"], required = false) var baseUrl: String? = null
    @Option(names = ["--modelName"], description = ["Model name for the LLM"], required = false) var modelName: String? = null
    @Option(names = ["--apiKey"], description = ["API key for the LLM"], required = false) var apiKey: String? = null
    @Option(names = ["--timeoutSeconds"], description = ["Timeout in seconds for the LLM requests"], required = false) var timeoutSeconds: Long? = null
    @Option(names = ["--temperature"], description = ["Temperature setting for the LLM"], required = false) var temperature: Double? = null
    @Option(names = ["--topP"], description = ["Top-p setting for the LLM"], required = false) var topP: Double? = null
    @Option(names = ["--extractOnly"], description = ["Only run Step 1 (activity extraction), skip GDPR classification"]) var extractOnly: Boolean = false
    @Option(names = ["--useRag"], description = ["Use RAG context for the GDPR classification"]) var useRag: Boolean = false
    @Option(names = ["--ragMode"], defaultValue = "HYBRID", description = ["RAG search mode: naive, local, global, hybrid (default)"]) lateinit var ragMode: RagMode

    override fun run() {
        log.info { "Running text analysis on: $textFilePath (extractOnly=$extractOnly, useRag=$useRag) with output format: $outputFormat" }
        val processText = Files.readString(textFilePath)

        val llm = LlmConfig.buildStrictJsonModelWithOverride(LlmPropsOverride(
            baseUrl = baseUrl,
            modelName = modelName,
            apiKey = apiKey,
            timeoutSeconds = timeoutSeconds,
            temperature = temperature,
            topP = topP
        ))

        if (extractOnly) {
            CliOutput.print(analyzerFactory.createTextActivityExtractor(llm).extractActivities(processText), outputFormat)
        } else {
            CliOutput.print(analyzerFactory.createTextAnalyzer(llm).analyzeTextForGdpr(processText, useRag, ragMode), outputFormat)
        }
    }
}
