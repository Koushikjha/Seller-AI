package com.marketplace.agent.llm;

import org.slf4j.Logger;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Two things that look like errors and are really just weather.
 *
 * RATE LIMITS. Free tiers cap tokens per minute, and one turn of a tool-using
 * agent is several requests each carrying a system prompt plus every tool
 * definition — so 429 is something to wait out, not something to surface to a
 * customer as "sorry, I lost my train of thought". Honours Retry-After when the
 * provider sends it, exponential backoff when it does not, and gives up rather
 * than hanging a request forever.
 *
 * MALFORMED TOOL ARGUMENTS. A model occasionally emits invalid JSON for its own
 * tool call — a stray quote, an unterminated string — and providers that
 * validate server-side return 400 rather than passing it through. That means
 * the lenient parsing in OpenAiCompatibleLlmClient never gets to see it. It is
 * a sampling accident, not a wrong prompt or a bad schema: the identical request
 * usually succeeds on the next attempt, because temperature makes each
 * generation a fresh roll. Retrying immediately is right, and cheap; failing the
 * whole turn over one bad token is neither.
 */
final class LlmRetry {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(3), Duration.ofSeconds(9), Duration.ofSeconds(20)
    };
    /** Beyond this a caller is better off failing fast than holding a thread. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    /**
     * No backoff for a bad generation. Nothing is overloaded and nothing needs
     * to cool down — we just want a different roll of the dice, now.
     */
    private static final Duration REGENERATE_PAUSE = Duration.ofMillis(250);

    private LlmRetry() {}

    static <T> T execute(String provider, Logger log, Supplier<T> call) {
        RuntimeException last = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();

            } catch (HttpClientErrorException.TooManyRequests e) {
                last = e;
                Duration wait = retryAfter(e).orElse(BACKOFF[Math.min(attempt, BACKOFF.length - 1)]);
                if (wait.compareTo(MAX_WAIT) > 0) {
                    log.warn("{} rate limited; provider asks for {}s which is too long to hold "
                            + "the request — failing fast", provider, wait.toSeconds());
                    throw rateLimited(provider, e);
                }
                log.warn("{} rate limited (attempt {}/{}), waiting {}s",
                        provider, attempt + 1, MAX_ATTEMPTS, wait.toSeconds());
                sleep(provider, wait);

            } catch (HttpClientErrorException.BadRequest e) {
                if (!isMalformedToolCall(e)) {
                    throw e;      // a real 400 — a bad schema or a bad body. Not our business.
                }
                last = e;
                log.warn("{} produced unparseable tool-call JSON (attempt {}/{}) — regenerating",
                        provider, attempt + 1, MAX_ATTEMPTS);
                log.debug("{} malformed generation: {}", provider, e.getResponseBodyAsString());
                sleep(provider, REGENERATE_PAUSE);
            }
        }

        if (last instanceof HttpClientErrorException.TooManyRequests limited) {
            throw rateLimited(provider, limited);
        }
        throw new LlmException(provider + " returned unparseable tool-call arguments "
                + MAX_ATTEMPTS + " times running. The model is struggling with the tool schema — "
                + "try a lower temperature, or a model with better function-calling support.", last);
    }

    /**
     * Groq reports this as code {@code tool_use_failed}; other OpenAI-compatible
     * providers word it differently, so match on the shape of the complaint
     * rather than one vendor's constant. A false positive here costs one wasted
     * retry; a false negative costs the customer their turn.
     */
    private static boolean isMalformedToolCall(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) return false;
        String lower = body.toLowerCase();
        return lower.contains("tool_use_failed")
                || lower.contains("failed to parse tool call")
                || (lower.contains("tool_call") && lower.contains("json"));
    }

    private static LlmException rateLimited(String provider, HttpClientErrorException e) {
        return new LlmException(provider + " is rate limited — the free tier caps tokens per "
                + "minute and this conversation is over it. Wait a moment, or use a smaller "
                + "model.", e);
    }

    private static void sleep(String provider, Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException(provider + " retry interrupted", ie);
        }
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