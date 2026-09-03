package com.marketplace.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the model behind the sales agent.
 *
 * Four providers, two implementations: Gemini has its own protocol, and Groq and
 * Cerebras share one because they are both OpenAI-compatible and differ only by
 * base URL and model id. Adding a fifth of that kind means a config block, not a
 * class.
 *
 * Nothing above this bean knows which one it got. That is the point — the sales
 * logic, the tool guarantees and the audit all live in the backend, so swapping
 * the model is an environment variable rather than a rewrite.
 */
@Configuration
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    @Bean
    LlmClient llmClient(AgentProperties props, ObjectMapper mapper) {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim().toLowerCase();

        return switch (provider) {
            case "groq"     -> openAiCompatible("groq", props.getGroq(), "GROQ_API_KEY", mapper);
            case "cerebras" -> openAiCompatible("cerebras", props.getCerebras(), "CEREBRAS_API_KEY", mapper);
            case "gemini"   -> {
                require(props.getGemini().getApiKey(), "gemini", "GEMINI_API_KEY");
                log.info("Sales agent using Gemini endpoint {} model {} ({})",
                        props.getGemini().getBaseUrl(), props.getGemini().getModel(),
                        fingerprint(props.getGemini().getApiKey()));
                yield new GeminiLlmClient(props.getGemini(), mapper);
            }
            case "", "scripted" -> {
                log.info("Sales agent using the scripted (offline) LLM client");
                yield new ScriptedLlmClient();
            }
            default -> throw new IllegalStateException(
                    "Unknown marketplace.agent.provider '" + props.getProvider()
                    + "'. Expected one of: scripted, gemini, groq, cerebras.");
        };
    }

    private LlmClient openAiCompatible(String provider, AgentProperties.OpenAiCompatible cfg,
                                       String envVar, ObjectMapper mapper) {
        require(cfg.getApiKey(), provider, envVar);
        log.info("Sales agent using {} endpoint {} model {} ({})",
                provider, cfg.getBaseUrl(), cfg.getModel(), fingerprint(cfg.getApiKey()));
        return new OpenAiCompatibleLlmClient(provider, cfg, mapper);
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
                    + "or set marketplace.agent." + provider + ".api-key. "
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
        return "key " + head + "… " + key.length() + " chars";
    }
}
