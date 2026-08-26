package com.marketplace.webinfo.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the whole webinfo path with no external dependency. The copy it returns
 * is deliberately hedged, because that is exactly how the agent is required to
 * phrase anything from this endpoint.
 */
@Component
@ConditionalOnProperty(name = "marketplace.webinfo.provider", havingValue = "stub", matchIfMissing = true)
public class StubWebSearchProvider implements WebSearchProvider {

    @Override public String name() { return "stub"; }

    @Override
    public Result search(String query) {
        String summary = """
                General, unverified notes for: %s

                Laptops in this product line typically ship with the manufacturer's own \
                companion utility for fan curves, performance modes and driver updates. \
                Driver and firmware updates are usually published through that utility \
                and the brand's support site; cadence varies by model and region. \
                Owners commonly report that performance modes make a noticeable \
                difference to fan noise under sustained load.

                None of the above is verified against the specific unit in stock. \
                Treat it as background colour only; anything price-, stock- or \
                spec-related must come from the catalog endpoints.""".formatted(query);
        return new Result(summary, 0);
    }
}
