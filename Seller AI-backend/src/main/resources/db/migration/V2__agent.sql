-- =====================================================================
-- V2 — Agent conversation + sales state.
--
-- Sales state lives HERE, not in the LLM's context window. The model gets
-- a rendered summary of this row each turn; it never owns the state.
-- That is what makes the conversation resumable, auditable, and testable.
--
-- conversation_message stores every tool call and its result, which is
-- what makes the "unauthorised claims" metric computable: replay the
-- transcript and check every figure the agent stated against the tool
-- results recorded alongside it.
-- =====================================================================

CREATE TABLE conversation (
    id                 BINARY(16)  NOT NULL,
    identity_key       VARCHAR(120),           -- set once verify_identity succeeds
    device_type        VARCHAR(20)  NOT NULL DEFAULT 'LAPTOP',
    stage              VARCHAR(30)  NOT NULL,
    technical_level    VARCHAR(20),            -- NOVICE / INTERMEDIATE / TECHNICAL
    budget_max         DECIMAL(10,2),

    requirements       JSON,                   -- what the agent has discovered
    candidate_ids      JSON,                   -- last search result ids
    objections         JSON,                   -- objection log

    negotiation_rounds INT NOT NULL DEFAULT 0, -- counted by the backend, never by the LLM
    questions_asked    INT NOT NULL DEFAULT 0,
    tool_calls_total   INT NOT NULL DEFAULT 0,

    selected_laptop_id BINARY(16),
    discount_offer_id  BINARY(16),
    order_id           BINARY(16),

    closed             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_conversation_identity FOREIGN KEY (identity_key)       REFERENCES verified_identity(identity_key),
    CONSTRAINT fk_conversation_laptop   FOREIGN KEY (selected_laptop_id) REFERENCES laptop(id),
    CONSTRAINT fk_conversation_offer    FOREIGN KEY (discount_offer_id)  REFERENCES discount_offer(id),
    CONSTRAINT fk_conversation_order    FOREIGN KEY (order_id)           REFERENCES marketplace_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_conversation_identity ON conversation(identity_key);
CREATE INDEX idx_conversation_stage    ON conversation(stage);

CREATE TABLE conversation_message (
    id              BINARY(16)  NOT NULL,
    conversation_id BINARY(16)  NOT NULL,
    seq             INT         NOT NULL,
    role            VARCHAR(20) NOT NULL,     -- USER / ASSISTANT / TOOL
    content         VARCHAR(8000),
    tool_name       VARCHAR(60),
    tool_args       JSON,
    tool_result     JSON,
    tool_ok         BOOLEAN,
    latency_ms      INT,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_message_seq UNIQUE (conversation_id, seq),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_message_conversation ON conversation_message(conversation_id, seq);
