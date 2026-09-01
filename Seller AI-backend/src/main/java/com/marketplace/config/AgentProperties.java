package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace.agent")
public class AgentProperties {

    /** 'gemini', 'groq', or 'scripted' for the deterministic offline fake. */
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

    public static class Groq {
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String model = "llama-3.3-70b-versatile";
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
}