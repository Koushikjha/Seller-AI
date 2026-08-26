package com.marketplace.webinfo.service;

/**
 * Seam for whatever search API you wire in (Tavily, Serper, Brave, ...).
 * Kept behind an interface so the cache, TTL and untrusted-text framing are
 * written once and are provider-independent.
 */
public interface WebSearchProvider {

    record Result(String summary, int sourceCount) {}

    String name();

    Result search(String query);
}
