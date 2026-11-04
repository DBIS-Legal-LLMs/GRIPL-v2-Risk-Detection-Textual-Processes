package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.adapter.web.utils.ControllerUtils
import de.mertendieckmann.griplbackend.config.LlmConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class ControllerUtilsTest {

    private val env = MockEnvironment().withProperty("TEST_API_KEY", "secret")

    @Test
    fun `missing llmProps stay null`() {
        val override: LlmConfig.Companion.LlmPropsOverride? = null
        assertNull(ControllerUtils.resolveEnvironmentVariables(override, env))
    }

    @Test
    fun `placeholders are resolved from the environment`() {
        val override = LlmConfig.Companion.LlmPropsOverride(modelName = "m", apiKey = "\${TEST_API_KEY}")
        val resolved = ControllerUtils.resolveEnvironmentVariables(override, env)
        assertEquals("secret", resolved?.apiKey)
        assertEquals("m", resolved?.modelName)
    }
}
