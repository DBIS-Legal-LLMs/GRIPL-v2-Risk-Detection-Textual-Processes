package de.mertendieckmann.griplbackend.application

import dev.langchain4j.service.output.OutputParsingException
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger { }

/**
 * Same number of retries as the [SafetyNet] of the BPMN analysis, so text and BPMN get the same number of chances.
 */
const val MAX_OUTPUT_PARSING_RETRIES = 3

/**
 * Retries [block] with a fresh LLM call when its output cannot be parsed, up to [maxRetries] times; returns the
 * result and the number of retries it took. Used for the text steps whose output quotes the process text
 * (extraction, text baseline): unlike the JSON repair of [SafetyNet], a fresh call does not carry stray tokens of a
 * broken output over into the quoted passages. [block] must start a new conversation on every call.
 */
fun <T> retryOnOutputParsingFailure(
    maxRetries: Int = MAX_OUTPUT_PARSING_RETRIES,
    block: () -> T
): Pair<T, Int> {
    require(maxRetries >= 0) { "maxRetries must be >= 0" }
    var attempt = 0
    while (true) {
        try {
            return Pair(block(), attempt)
        } catch (e: OutputParsingException) {
            if (attempt >= maxRetries) throw e
            attempt++
            log.warn(e) { "Parsing failed. Retrying with a fresh call... (Attempt $attempt of $maxRetries)" }
        }
    }
}
