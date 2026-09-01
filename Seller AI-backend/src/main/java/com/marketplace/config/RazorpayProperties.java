package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials and behaviour.
 *
 * Nothing here has a default that would let the app talk to a real account by
 * accident: with no key id and secret the stub gateway stays wired in, exactly
 * as it is today. Test-mode keys (rzp_test_...) and live keys go in the same
 * fields — Razorpay decides which environment you are in from the key itself,
 * which is why the key id is echoed on the /orders/payment-provider endpoint.
 * Reading "rzp_test_" back before a demo is cheaper than discovering you took a
 * real payment on stage.
 */
@ConfigurationProperties(prefix = "marketplace.razorpay")
public class RazorpayProperties {

    private String baseUrl = "https://api.razorpay.com/v1";

    /** rzp_test_xxxx for test mode. Blank leaves the stub gateway in place. */
    private String keyId = "";

    private String keySecret = "";

    /**
     * The secret you typed into the Razorpay dashboard when creating the webhook —
     * NOT the API secret. Blank means webhook calls are rejected rather than
     * trusted, because an unverified webhook is an open endpoint that marks
     * orders paid.
     */
    private String webhookSecret = "";

    /** Where Razorpay sends the customer after payment. */
    private String callbackUrl = "http://localhost:8080/?payment=done";

    /** Payment link lifetime. Razorpay requires at least 15 minutes. */
    private int expiryMinutes = 30;

    private int timeoutSeconds = 20;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String v) { this.keyId = v; }
    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String v) { this.keySecret = v; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String v) { this.webhookSecret = v; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String v) { this.callbackUrl = v; }
    public int getExpiryMinutes() { return expiryMinutes; }
    public void setExpiryMinutes(int v) { this.expiryMinutes = v; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }

    public boolean configured() {
        return keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }

    /** Test mode is visible in the key itself. Worth surfacing, never worth guessing. */
    public boolean testMode() {
        return keyId != null && keyId.startsWith("rzp_test_");
    }
}
