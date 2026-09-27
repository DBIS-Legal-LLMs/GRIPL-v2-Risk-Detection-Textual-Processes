package de.mertendieckmann.griplbackend.adapter.cli

import de.mertendieckmann.griplbackend.application.analyzer.AnalyzerFactory
import de.mertendieckmann.griplbackend.config.LlmConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import de.mertendieckmann.griplbackend.config.LlmConfig.Companion.LlmPropsOverride

/**
 * Basic CLI test harness for Step 1 (activity extraction) of the text-analysis pipeline —
 * lets extraction quality be inspected on plain-text process descriptions without any frontend.
 */
@Command(
    name = "text-analysis",
    description = ["Extract process activities from a textual process description (Step 1 of the text-analysis pipeline, no GDPR classification yet)"],
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

    override fun run() {
        log.info { "Running text activity extraction on: $textFilePath with output format: $outputFormat" }
        val processText = Files.readString(textFilePath)

        val llm = LlmConfig.buildStrictJsonModelWithOverride(LlmPropsOverride(
            baseUrl = baseUrl,
            modelName = modelName,
            apiKey = apiKey,
            timeoutSeconds = timeoutSeconds,
            temperature = temperature,
            topP = topP
        ))

        val extractor = analyzerFactory.createTextActivityExtractor(llm)
        val result = extractor.extractActivities(processText)
        CliOutput.print(result, outputFormat)
    }
}
