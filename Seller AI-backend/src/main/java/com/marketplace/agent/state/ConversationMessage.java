package com.marketplace.agent.state;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One turn. TOOL rows carry the call and its result verbatim, which is what
 * makes the hallucination audit possible: every figure the assistant states
 * should be traceable to a tool_result recorded before it.
 */
@Entity
@Table(name = "conversation_message")
@Getter
@Setter
public class ConversationMessage {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(length = 8000)
    private String content;

    @Column(name = "tool_name", length = 60)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_args")
    private Map<String, Object> toolArgs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_result")
    private Map<String, Object> toolResult;

    @Column(name = "tool_ok")
    private Boolean toolOk;

    /**
     * Provider-opaque state for this call — currently Gemini's thoughtSignature.
     * History is rebuilt from these rows every turn, so anything the provider
     * demands back verbatim has to survive here or multi-turn tool use breaks.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_meta")
    private Map<String, Object> toolMeta;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}