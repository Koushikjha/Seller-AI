-- V3 — Provider-opaque metadata on tool calls.
--
-- Gemini 3 attaches a `thoughtSignature` to each functionCall part and rejects
-- the next request with a 400 unless that signature is echoed back, unchanged,
-- in the exact part it arrived on. Conversation history is rebuilt from these
-- rows every turn, so the signature has to survive in the database or
-- multi-turn tool use breaks.
--
-- JSON rather than VARCHAR: InnoDB keeps JSON off-page, so a long signature
-- costs almost nothing against the 65,535-byte row limit that
-- `content VARCHAR(8000)` already eats into. A map, so other providers'
-- opaque state fits here too.

ALTER TABLE conversation_message ADD COLUMN tool_meta JSON;
