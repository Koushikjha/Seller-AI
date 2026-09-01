package com.marketplace.analytics;

import com.marketplace.agent.state.ConversationMessage;
import com.marketplace.agent.state.ConversationMessageRepository;
import com.marketplace.agent.state.ConversationRepository;
import com.marketplace.agent.state.MessageRole;
import com.marketplace.common.NotFoundException;
import com.marketplace.laptop.repository.LaptopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays a transcript and checks every commerce claim the assistant made
 * against what it had actually been told at that point in the conversation.
 *
 * This is the measurement that separates this from a chatbot. Anyone can say
 * their agent does not hallucinate; this counts. It works only because every
 * tool call and result is persisted alongside the messages — the audit reads
 * the same rows the agent was given, in the same order.
 *
 * Three sources of truth, ranked:
 *   TOOL     — the backend returned it. The agent may state it as fact.
 *   CUSTOMER — the customer said it. Echoing "your ₹90,000 budget" back is
 *              not a fabrication, but it is not a shop fact either.
 *   nothing  — the agent produced it. This is the number that matters.
 *
 * Precision matters more than recall here. An audit that flags sixteen things
 * and is wrong sixteen times teaches you to ignore it, which is worse than not
 * running one. So:
 *   - digits inside any string a tool returned count as known, because
 *     "RTX 4060" and "Ryzen 7 7840HS" contain numbers that are not prices;
 *   - numbers the customer used are tracked separately, not counted as errors;
 *   - small numbers are ignored entirely as prose ("two options", "144Hz").
 *
 * What it still cannot do: catch a wholly invented product that is absent from
 * the catalog, or a fabricated figure that happens to match a real one.
 */
@Service
@Transactional(readOnly = true)
public class HallucinationAuditService {

    /** ₹72,990 · 72990 · 91,190.40 — four or more digits once separators are stripped. */
    private static final Pattern MONEY = Pattern.compile("₹?\\s?(\\d[\\d,]{3,}(?:\\.\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s?%");
    /** Digit runs inside a string fact: "rtx 4060", "i5-13420h", "ryzen 7 7840hs". */
    private static final Pattern EMBEDDED_DIGITS = Pattern.compile("\\d{3,}");

    public static final String FROM_TOOL = "TOOL";
    public static final String FROM_CUSTOMER = "CUSTOMER";
    public static final String UNSUPPORTED = "NONE";

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final LaptopRepository laptops;

    public HallucinationAuditService(ConversationRepository conversations,
                                     ConversationMessageRepository messages,
                                     LaptopRepository laptops) {
        this.conversations = conversations;
        this.messages = messages;
        this.laptops = laptops;
    }

    public record Claim(int seq, String type, String value, String source, String excerpt) {
        public boolean isUnsupported() { return UNSUPPORTED.equals(source); }
    }

    public Map<String, Object> audit(UUID conversationId) {
        if (!conversations.existsById(conversationId)) {
            throw new NotFoundException("Conversation", conversationId);
        }
        List<String> catalogModels = laptops.findAll().stream()
                .map(l -> l.getModelName()).filter(Objects::nonNull).toList();

        Set<String> toolFacts = new HashSet<>();
        Set<String> customerFacts = new HashSet<>();
        List<Claim> claims = new ArrayList<>();

        for (ConversationMessage m : messages.findByConversationIdOrderBySeqAsc(conversationId)) {
            switch (m.getRole()) {
                case TOOL -> collect(m.getToolResult(), toolFacts);
                case USER -> collectFromText(m.getContent(), customerFacts);
                case ASSISTANT -> {
                    if (m.getContent() != null) {
                        claims.addAll(check(m, catalogModels, toolFacts, customerFacts));
                    }
                }
            }
        }

        List<Claim> unsupported = claims.stream().filter(Claim::isUnsupported).toList();
        long echoed = claims.stream().filter(c -> FROM_CUSTOMER.equals(c.source())).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("claimsChecked", claims.size());
        out.put("supportedByTool", claims.size() - unsupported.size() - echoed);
        out.put("echoedFromCustomer", echoed);
        out.put("unsupported", unsupported.size());
        out.put("verdict", unsupported.isEmpty()
                ? "CLEAN — every figure traces to a tool result or to something the customer said"
                : unsupported.size() + " figure(s) the agent produced from nowhere");
        out.put("unsupportedClaims", unsupported);
        out.put("allClaims", claims);
        out.put("method", "Money (4+ digits), percentages and catalog model names, checked against "
                + "tool results and customer statements earlier in the same conversation. Digits "
                + "inside returned strings (RTX 4060, Ryzen 7 7840HS) count as known. Cannot detect "
                + "an invented product absent from the catalog.");
        return out;
    }

    /** Fleet-wide view: how often does the agent say something it was never told? */
    public Map<String, Object> auditAll() {
        int checked = 0, unsupported = 0, dirty = 0;
        List<Map<String, Object>> offenders = new ArrayList<>();

        for (var conv : conversations.findAll()) {
            Map<String, Object> a = audit(conv.getId());
            int c = (int) a.get("claimsChecked");
            int u = (int) a.get("unsupported");
            checked += c;
            unsupported += u;
            if (u > 0) {
                dirty++;
                offenders.add(Map.of(
                        "conversationId", conv.getId(),
                        "unsupported", u,
                        "claims", a.get("unsupportedClaims")));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationsAudited", conversations.count());
        out.put("conversationsWithUnsupportedClaims", dirty);
        out.put("claimsChecked", checked);
        out.put("unsupportedClaims", unsupported);
        out.put("accuracy", checked == 0 ? "n/a"
                : String.format("%.1f%%", 100.0 * (checked - unsupported) / checked));
        out.put("offenders", offenders);
        return out;
    }

    // ------------------------------------------------------------------

    private List<Claim> check(ConversationMessage m, List<String> catalogModels,
                              Set<String> toolFacts, Set<String> customerFacts) {
        String text = m.getContent();
        List<Claim> found = new ArrayList<>();

        Matcher money = MONEY.matcher(text);
        while (money.find()) {
            String norm = normalizeNumber(money.group(1));
            if (norm == null) continue;
            found.add(new Claim(m.getSeq(), "MONEY", money.group(1),
                    sourceOf(norm, toolFacts, customerFacts), excerpt(text, money.start())));
        }

        Matcher pct = PERCENT.matcher(text);
        while (pct.find()) {
            String norm = normalizeNumber(pct.group(1));
            found.add(new Claim(m.getSeq(), "PERCENT", pct.group(1) + "%",
                    norm == null ? UNSUPPORTED : sourceOf(norm, toolFacts, customerFacts),
                    excerpt(text, pct.start())));
        }

        String lower = text.toLowerCase();
        for (String model : catalogModels) {
            String key = model.toLowerCase();
            if (lower.contains(key)) {
                found.add(new Claim(m.getSeq(), "PRODUCT", model,
                        sourceOf(key, toolFacts, customerFacts), excerpt(text, lower.indexOf(key))));
            }
        }
        return found;
    }

    private String sourceOf(String value, Set<String> toolFacts, Set<String> customerFacts) {
        if (toolFacts.contains(value)) return FROM_TOOL;
        if (customerFacts.contains(value)) return FROM_CUSTOMER;
        return UNSUPPORTED;
    }

    /** Every scalar a tool returned, flattened — plus digit runs inside strings. */
    private void collect(Object node, Set<String> facts) {
        if (node == null) return;
        if (node instanceof Map<?, ?> map) {
            map.values().forEach(v -> collect(v, facts));
        } else if (node instanceof Iterable<?> it) {
            it.forEach(v -> collect(v, facts));
        } else if (node instanceof Number n) {
            String norm = normalizeNumber(n.toString());
            if (norm != null) facts.add(norm);
        } else if (!(node instanceof Boolean)) {
            collectFromText(String.valueOf(node), facts);
        }
    }

    /**
     * Index a string as a fact, and index the numbers embedded in it separately —
     * otherwise "RTX 4060" being returned by a tool does not stop the agent's
     * mention of 4060 looking like an invented price.
     */
    private void collectFromText(String s, Set<String> facts) {
        if (s == null || s.isBlank()) return;
        facts.add(s.toLowerCase());

        String norm = normalizeNumber(s);
        if (norm != null) facts.add(norm);

        Matcher digits = EMBEDDED_DIGITS.matcher(s.replace(",", ""));
        while (digits.find()) {
            String d = normalizeNumber(digits.group());
            if (d != null) facts.add(d);
        }
    }

    /** "₹72,990" · "72990.00" · "4.00" all collapse to the same key. */
    private String normalizeNumber(String raw) {
        String cleaned = raw.replace(",", "").replace("₹", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            double d = Double.parseDouble(cleaned);
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String excerpt(String text, int at) {
        int from = Math.max(0, at - 45);
        int to = Math.min(text.length(), at + 55);
        return (from > 0 ? "…" : "") + text.substring(from, to).replace("\n", " ")
                + (to < text.length() ? "…" : "");
    }
}