package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Every merchant-policy number lives here, not in code and never in a prompt.
 * Changing negotiation behaviour is a config change, not a model change.
 */
@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceProperties {

    private Discount discount = new Discount();
    private Identity identity = new Identity();
    private WebInfo webinfo = new WebInfo();
    private Seed seed = new Seed();

    public Discount getDiscount() { return discount; }
    public void setDiscount(Discount d) { this.discount = d; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity i) { this.identity = i; }
    public WebInfo getWebinfo() { return webinfo; }
    public void setWebinfo(WebInfo w) { this.webinfo = w; }
    public Seed getSeed() { return seed; }
    public void setSeed(Seed s) { this.seed = s; }

    public static class Discount {
        private BigDecimal basePct = BigDecimal.valueOf(2);
        private BigDecimal perRoundBonusPct = BigDecimal.valueOf(2);
        private int maxRoundsCounted = 3;
        private int offerTtlMinutes = 30;
        private BigDecimal repeatBuyerCapPct = BigDecimal.ONE;
        private int repeatLookBackDays = 30;

        public BigDecimal getBasePct() { return basePct; }
        public void setBasePct(BigDecimal v) { this.basePct = v; }
        public BigDecimal getPerRoundBonusPct() { return perRoundBonusPct; }
        public void setPerRoundBonusPct(BigDecimal v) { this.perRoundBonusPct = v; }
        public int getMaxRoundsCounted() { return maxRoundsCounted; }
        public void setMaxRoundsCounted(int v) { this.maxRoundsCounted = v; }
        public int getOfferTtlMinutes() { return offerTtlMinutes; }
        public void setOfferTtlMinutes(int v) { this.offerTtlMinutes = v; }
        public BigDecimal getRepeatBuyerCapPct() { return repeatBuyerCapPct; }
        public void setRepeatBuyerCapPct(BigDecimal v) { this.repeatBuyerCapPct = v; }
        public int getRepeatLookBackDays() { return repeatLookBackDays; }
        public void setRepeatLookBackDays(int v) { this.repeatLookBackDays = v; }
    }

    public static class Identity {
        private int maxVerificationsPerIpPerHour = 5;
        public int getMaxVerificationsPerIpPerHour() { return maxVerificationsPerIpPerHour; }
        public void setMaxVerificationsPerIpPerHour(int v) { this.maxVerificationsPerIpPerHour = v; }
    }

    public static class WebInfo {
        private int cacheTtlDays = 7;
        private String provider = "stub";
        public int getCacheTtlDays() { return cacheTtlDays; }
        public void setCacheTtlDays(int v) { this.cacheTtlDays = v; }
        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
    }

    public static class Seed {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }
}
