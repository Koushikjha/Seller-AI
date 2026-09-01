package com.marketplace.agent.llm;

import org.slf4j.Logger;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate limits are normal operation for an LLM API, not an error.
 *
 * Free tiers cap tokens per minute, and one turn of a tool-using agent is
 * several requests carrying a system prompt plus every tool definition — so
 * 429 is something to wait out, not something to surface to a customer as
 * "sorry, I lost my train of thought".
 *
 * Honours Retry-After when the provider sends it, falls back to exponential
 * backoff when it does not, and gives up rather than hanging a request
 * forever.
 */
final class LlmRetry {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(3), Duration.ofSeconds(9), Duration.ofSeconds(20)
    };
    /** Beyond this a caller is better off failing fast than holding a thread. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    private LlmRetry() {}

    static <T> T execute(String provider, Logger log, Supplier<T> call) {
        HttpClientErrorException.TooManyRequests last = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                last = e;
                Duration wait = retryAfter(e).orElse(BACKOFF[Math.min(attempt, BACKOFF.length - 1)]);
                if (wait.compareTo(MAX_WAIT) > 0) {
                    log.warn("{} rate limited; provider asks for {}s which is too long to hold "
                            + "the request — failing fast", provider, wait.toSeconds());
                    break;
                }
                log.warn("{} rate limited (attempt {}/{}), waiting {}s",
                        provider, attempt + 1, MAX_ATTEMPTS, wait.toSeconds());
                try {
                    Thread.sleep(wait.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException(provider + " retry interrupted", ie);
                }
            }
        }

        throw new LlmException(provider + " is rate limited — the free tier caps tokens per "
                + "minute and this conversation is over it. Wait a moment, or use a smaller "
                + "model.", last);
    }

    private static java.util.Optional<Duration> retryAfter(HttpClientErrorException e) {
        var headers = e.getResponseHeaders();
        if (headers == null) return java.util.Optional.empty();
        String value = headers.getFirst("retry-after");
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        try {
            // Seconds, occasionally fractional.
            return java.util.Optional.of(
                    Duration.ofMillis((long) (Double.parseDouble(value.trim()) * 1000)));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}