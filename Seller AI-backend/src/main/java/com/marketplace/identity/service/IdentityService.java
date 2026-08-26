package com.marketplace.identity.service;

import com.marketplace.common.BusinessRuleException;
import com.marketplace.config.MarketplaceProperties;
import com.marketplace.identity.entity.VerifiedIdentity;
import com.marketplace.identity.repository.VerifiedIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@Transactional
public class IdentityService {

    private final VerifiedIdentityRepository repo;
    private final MarketplaceProperties props;

    public IdentityService(VerifiedIdentityRepository repo, MarketplaceProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /**
     * Stubbed OTP: in the prototype, presenting an identity key verifies it.
     * The rate limit is real -- it is what stops one IP minting a hundred
     * identities to reset the negotiation ladder.
     */
    public VerifiedIdentity verify(String identityKey, String ipAddress) {
        if (identityKey == null || identityKey.isBlank()) {
            throw new IllegalArgumentException("identityKey is required");
        }
        String key = identityKey.trim().toLowerCase();

        var existing = repo.findById(key);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant windowStart = Instant.now().minus(Duration.ofHours(1));
        long recent = repo.countByIpAddressAndVerifiedAtAfter(ipAddress, windowStart);
        int limit = props.getIdentity().getMaxVerificationsPerIpPerHour();
        if (recent >= limit) {
            throw new BusinessRuleException("IDENTITY_RATE_LIMITED",
                    "Too many identity verifications from this IP in the last hour",
                    Map.of("limitPerHour", limit, "seenInWindow", recent));
        }

        VerifiedIdentity vi = new VerifiedIdentity();
        vi.setIdentityKey(key);
        vi.setIpAddress(ipAddress);
        vi.setVerifiedAt(Instant.now());
        return repo.save(vi);
    }

    @Transactional(readOnly = true)
    public VerifiedIdentity require(String identityKey) {
        if (identityKey == null || identityKey.isBlank()) {
            throw new IllegalArgumentException("identityKey is required");
        }
        return repo.findById(identityKey.trim().toLowerCase())
                .orElseThrow(() -> new BusinessRuleException("IDENTITY_NOT_VERIFIED",
                        "This identity has not been verified. Call POST /identity/verify first.",
                        Map.of("identityKey", identityKey)));
    }

    @Transactional(readOnly = true)
    public boolean isVerified(String identityKey) {
        return identityKey != null && repo.existsById(identityKey.trim().toLowerCase());
    }
}
