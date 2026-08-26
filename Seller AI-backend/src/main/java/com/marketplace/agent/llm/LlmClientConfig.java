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
            if (isBlank(props.getGroq().getApiKey())) {
                log.warn("marketplace.agent.provider=groq but no API key is set "
                        + "(GROQ_API_KEY). Falling back to the scripted client.");
                return new ScriptedLlmClient();
            }
            log.info("Sales agent using Groq model {}", props.getGroq().getModel());
            return new GroqLlmClient(props.getGroq(), mapper);
        }
        if ("gemini".equalsIgnoreCase(props.getProvider())) {
            if (isBlank(props.getGemini().getApiKey())) {
                log.warn("marketplace.agent.provider=gemini but no API key is set "
                        + "(GEMINI_API_KEY). Falling back to the scripted client.");
                return new ScriptedLlmClient();
            }
            log.info("Sales agent using Gemini model {}", props.getGemini().getModel());
            return new GeminiLlmClient(props.getGemini(), mapper);
        }
        log.info("Sales agent using the scripted (offline) LLM client");
        return new ScriptedLlmClient();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}