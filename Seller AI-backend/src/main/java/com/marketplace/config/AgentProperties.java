package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace.agent")
public class AgentProperties {

    /** 'gemini', 'groq', 'cerebras', or 'scripted' for the deterministic offline fake. */
    private String provider = "scripted";

    /** Hard stop on tool calls inside one user turn — a runaway loop guard. */
    private int maxToolIterations = 6;

    /**
     * How many of the most recent tool results are replayed in full when the
     * conversation history is rebuilt. Older ones are compacted to ids, names
     * and prices — enough for the agent to keep referring to them, without
     * resending several kilobytes of specs on every subsequent turn.
     */
    private int fullToolResultsInHistory = 2;

    private Gemini gemini = new Gemini();
    private Groq groq = new Groq();
    private Cerebras cerebras = new Cerebras();

    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public int getMaxToolIterations() { return maxToolIterations; }
    public void setMaxToolIterations(int v) { this.maxToolIterations = v; }
    public int getFullToolResultsInHistory() { return fullToolResultsInHistory; }
    public void setFullToolResultsInHistory(int v) { this.fullToolResultsInHistory = v; }
    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini v) { this.gemini = v; }
    public Groq getGroq() { return groq; }
    public void setGroq(Groq v) { this.groq = v; }
    public Cerebras getCerebras() { return cerebras; }
    public void setCerebras(Cerebras v) { this.cerebras = v; }

    public static class Gemini {
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-3.6-flash";
        private String apiKey = "";
        private double temperature = 0.7;
        private int timeoutSeconds = 45;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double v) { this.temperature = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }

    /**
     * Settings for any provider that speaks OpenAI's /chat/completions.
     *
     * Groq and Cerebras differ by base URL and model id and by nothing else on
     * the wire, which we established the hard way by pointing the Groq client at
     * Cerebras and watching it work. Two subclasses that supply their own
     * defaults, one client, no duplicated protocol code — and a fourth provider
     * of this kind is a config block rather than a class.
     */
    public static class OpenAiCompatible {
        private String baseUrl;
        private String model;
        private String apiKey = "";
        private double temperature = 0.7;
        private int timeoutSeconds = 45;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double v) { this.temperature = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }

    public static class Groq extends OpenAiCompatible {
        public Groq() {
            setBaseUrl("https://api.groq.com/openai/v1");
            setModel("llama-3.3-70b-versatile");
        }
    }

    /**
     * Cerebras. Note the model ids carry NO vendor prefix here: the model that
     * Groq calls {@code openai/gpt-oss-120b} is plain {@code gpt-oss-120b} on
     * Cerebras. Confirm against your own key with
     * {@code GET https://api.cerebras.ai/v1/models} rather than the docs —
     * published model names on both providers have gone stale under us before.
     */
    public static class Cerebras extends OpenAiCompatible {
        public Cerebras() {
            setBaseUrl("https://api.cerebras.ai/v1");
            setModel("gpt-oss-120b");
        }
    }
}
