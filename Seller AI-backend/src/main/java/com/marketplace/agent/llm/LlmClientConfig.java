package com.marketplace.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    @Bean
    LlmClient llmClient(AgentProperties props, ObjectMapper mapper) {
        if ("groq".equalsIgnoreCase(props.getProvider())) {
            require(props.getGroq().getApiKey(), "groq", "GROQ_API_KEY");
            log.info("Sales agent using Groq-compatible endpoint {} model {} ({})",
                    props.getGroq().getBaseUrl(), props.getGroq().getModel(),
                    fingerprint(props.getGroq().getApiKey()));
            return new GroqLlmClient(props.getGroq(), mapper);
        }
        if ("gemini".equalsIgnoreCase(props.getProvider())) {
            require(props.getGemini().getApiKey(), "gemini", "GEMINI_API_KEY");
            log.info("Sales agent using Gemini endpoint {} model {} ({})",
                    props.getGemini().getBaseUrl(), props.getGemini().getModel(),
                    fingerprint(props.getGemini().getApiKey()));
            return new GeminiLlmClient(props.getGemini(), mapper);
        }
        log.info("Sales agent using the scripted (offline) LLM client");
        return new ScriptedLlmClient();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Fail the boot rather than silently substituting the fake model.
     *
     * The old behaviour logged a warning and returned ScriptedLlmClient. That is
     * the worst possible failure mode: the app starts, the API answers, the
     * answers look reasonable — and you spend twenty minutes wondering why your
     * prompt changes have no effect. A missing key is a configuration error, so
     * it should read like one.
     */
    private static void require(String key, String provider, String envVar) {
        if (isBlank(key)) {
            throw new IllegalStateException(
                    "marketplace.agent.provider=" + provider + " but no API key is set. "
                            + "Set " + envVar + " in the environment this JVM was started from, "
                            + "or set marketplace." + provider + ".api-key. "
                            + "(To run offline on purpose, use AGENT_PROVIDER=scripted.)");
        }
    }

    /**
     * Enough of the key to tell two of them apart, not enough to use.
     *
     * A 401 with a key that works fine in curl means the process is holding a
     * different key from the shell you tested in — a stale export, or an IDE
     * that never saw your profile. Printing a fingerprint at startup turns that
     * from a twenty-minute hunt into a glance.
     */
    private static String fingerprint(String key) {
        if (isBlank(key)) return "no key";
        String head = key.length() <= 10 ? key : key.substring(0, 10);
        return "key " + head + "\u2026 " + key.length() + " chars";
    }
}