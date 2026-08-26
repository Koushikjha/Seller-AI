package com.marketplace.common;

/**
 * A request that is well-formed but not permitted by merchant rules
 * (expired offer, out of stock, unverified identity, ...).
 * These are returned to the agent as structured, actionable failures --
 * never as free text it might paraphrase into a false promise.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final Object details;

    public BusinessRuleException(String code, String message) {
        this(code, message, null);
    }

    public BusinessRuleException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() { return code; }
    public Object getDetails() { return details; }
}
