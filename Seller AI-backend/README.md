# Seller AI

An autonomous AI sales agent that behaves like a shopkeeper — discovers needs, handles
objections, negotiates within merchant-set limits, and closes the sale. The LLM runs the
conversation; the backend owns every price, spec and stock figure, so the model cannot
invent one.

Java 21 · Spring Boot 3.3 · MySQL 8 · Flyway · React (CDN, no build step) · Razorpay

Four LLM providers behind one interface — Gemini, Groq, Cerebras, OpenRouter — plus a
deterministic offline client, switchable with an environment variable.

> **The AI controls sales strategy and conversation. The backend controls business truth and execution.**

---

## Run it

One process runs the whole product — API, agent and UI.

```bash
docker compose up -d          # MySQL on :3306
mvn spring-boot:run           # everything on :8080
```

| | |
|---|---|
| **http://localhost:8080/** | the shop — customer chat, product cards, live agent activity |
| **http://localhost:8080/admin.html** | merchant dashboard — inventory, discount ceilings, conversations, metrics |
| http://localhost:8080/swagger-ui.html | every endpoint |

**It runs with no API key.** The agent defaults to a deterministic offline client, so the
whole loop works out of the box. For a real model:

```bash
AGENT_PROVIDER=groq     GROQ_API_KEY=...     GROQ_MODEL=openai/gpt-oss-120b   mvn spring-boot:run
AGENT_PROVIDER=gemini   GEMINI_API_KEY=...   GEMINI_MODEL=gemini-3.6-flash    mvn spring-boot:run
AGENT_PROVIDER=cerebras CEREBRAS_API_KEY=... CEREBRAS_MODEL=gpt-oss-120b      mvn spring-boot:run
```

Groq and Cerebras share one client — both speak OpenAI `/chat/completions` and differ only
by base URL and model id. Any other provider of that kind needs no Java, just a base URL:

```bash
AGENT_PROVIDER=groq GROQ_BASE_URL=https://openrouter.ai/api/v1 \
  GROQ_API_KEY=$OPENROUTER_KEY GROQ_MODEL=openai/gpt-oss-120b:free mvn spring-boot:run
```

**Check the model name against your own key first** — published names go stale, and the same
model is `openai/gpt-oss-120b` on Groq but plain `gpt-oss-120b` on Cerebras:

```bash
curl -s -H "Authorization: Bearer $KEY" https://api.groq.com/openai/v1/models | jq -r '.data[].id'
```

A missing key **fails the boot** rather than silently falling back to the offline client. An
app that starts, answers, and sounds plausible while ignoring every prompt change is the
worst failure mode there is.

Rate limits are token-per-minute on most free tiers, and one agent turn is several requests
each carrying the system prompt and tool schemas. If you see 429s, check the model's TPM cap
before blaming the code — and note that `full-tool-results-in-history` and stage-gated tools
(below) exist to keep that number down.

```bash
curl localhost:8080/agent/manifest | jq
curl -s localhost:8080/chat -H 'Content-Type: application/json' \
  -d '{"message":"gaming laptop under 90000"}' | jq '.data | {reply, toolCalls}'
```

**Demoing it?** `docs/DEMO_SCRIPT.md` is a five-minute walkthrough — what to type, what to
say, what to show, and what to do when a take goes wrong.

---

## The frontend

Two static files in `src/main/resources/static/`, served by Spring Boot itself. React via
CDN, no npm, no build, no CORS — same origin as the API.

**The shop** shows the conversation, product cards built from the agent's `products`, and
a live **"what the agent just did"** panel listing every tool call with its arguments and
latency. That panel is the point: agentic behaviour you can watch rather than take on
faith.

**The merchant dashboard** is where the shop is actually controlled. Edit stock to zero and
the agent can no longer surface that model — search filters it out before the model sees
it. Set a discount ceiling to zero and the price is firm however the customer negotiates.
Conversion, revenue, discount exposure and objection counts are computed from rows the
agent cannot write.

If you later want a separate Vite/React app, the contract is already stable:
`POST /chat` returns `{reply, stage, toolCalls, products[]}`, where each product carries
the backend's record and the agent's own stated reason for showing it.

Tests run against in-memory H2 — no database needed:

```bash
mvn test
```

> **Run `mvn test` first, then boot against a real MySQL.** The build has not been compiled in the
> environment it was written in (no Maven Central access there), so treat the first green test run
> as the acceptance gate for the Java — and note that tests use H2 with `ddl-auto: create-drop`, so
> they never execute `V1__schema.sql`. The migration is only validated the first time you start the
> app against MySQL with `ddl-auto: validate`.
>
> If schema validation complains about a column type, generate the truth rather than guessing:
> point the app at an empty MySQL database with `ddl-auto: create` and Flyway disabled, then
> `mysqldump --no-data marketplace` and make `V1__schema.sql` match what Hibernate produced.

### MySQL specifics

- **IDs are `BINARY(16)`.** `SELECT id FROM laptop` returns binary garbage; use
  `SELECT BIN_TO_UUID(id) FROM laptop`.
- **Random UUIDs are a worse primary key on MySQL than on Postgres.** InnoDB clusters rows on the
  primary key, so v4 UUIDs scatter inserts across the B-tree and fragment it. It will not matter at
  this scale, but if the catalog ever grows, `@UuidGenerator(style = Style.TIME)` on the entities
  gives you time-ordered IDs and sequential inserts. Postgres heap tables do not have this problem,
  which is why it was not a consideration before.
- **Failed migrations do not roll back.** MySQL commits implicitly on DDL, so a migration that dies
  halfway leaves a partial schema and a failed Flyway entry — `flyway repair`, or drop the database
  and start over. Postgres would have rolled the whole thing back.
- **Comparisons are case-insensitive** under `utf8mb4_0900_ai_ci`. `LIKE` filters
  (`modelNameContains`, `os`) now match regardless of case, and `brand.name UNIQUE` treats "ASUS"
  and "asus" as the same brand. Both are fine here; just don't be surprised.

---

## Module map

```
com.marketplace
├── catalog/
│   ├── core/        DeviceType, CatalogProvider, CatalogItemView, CatalogQuery,
│   │                SpecKey, SpecKeyValidator, CatalogRegistry   <- the extension point
│   ├── entity/      Brand, SubBrand, Cpu, Gpu                     <- shared reference data
│   ├── repository/  service/  controller/  dto/
│
├── laptop/          the fully-built device type
│   ├── entity/      Laptop, DiscountOffer, MarketplaceOrder, OrderStatus
│   ├── spec/        ExtraSpecKey (whitelist), LaptopSpecifications (all search predicates)
│   ├── service/     LaptopService, LaptopSearchService, LaptopCompareService,
│   │                DiscountApprovalPolicy, DiscountService, OrderService,
│   │                PaymentGateway + default stub, LaptopCatalogProvider
│   ├── repository/  controller/  dto/
│
├── smartphone/      thin second device type — proves the extension point
│   └── entity/ repository/ service/ spec/
│
├── identity/        verified phone/email, per-IP rate limit
├── webinfo/         soft, web-sourced info cached per sub-brand
├── agent/           the agent itself
│   ├── llm/         LlmClient + Gemini, OpenAiCompatible (Groq/Cerebras/…), scripted fake
│   ├── tool/        ToolExecutor — where the structural guarantees live
│   ├── state/       Conversation, ConversationMessage — the sales state outside the LLM
│   └── orchestrator/ SalesAgentService (the loop), SystemPromptBuilder
├── analytics/       conversation metrics + the hallucination audit
├── common/          ApiResponse envelope, exceptions, global handler
└── config/          MarketplaceProperties, AgentProperties, RazorpayProperties, seed loader
```

Adding a device type: new package, an entity, a `SpecKey` enum, a class implementing
`CatalogProvider`, one migration. `catalog/`, `laptop/`, `agent/` and every existing endpoint stay
untouched — `CatalogRegistry` discovers the new provider and `/catalog/*` plus `/agent/manifest`
pick it up automatically. `smartphone/` is that path walked end to end so you can see the shape.

---

## Endpoints

Every response is wrapped: `{ ok, data, error: { code, message, details }, at }`.
The agent can branch on `ok` without parsing HTTP status codes.

### catalog
| | |
|---|---|
| `GET /brands` | every brand |
| `GET /sub-brands?brandId=` | product lines |
| `GET /cpus` · `GET /gpus` | processors and graphics |
| `GET /catalog/device-types` | sellable categories + their spec vocabulary |
| `GET /catalog/search?deviceType=…` | device-agnostic search |
| `GET /catalog/{deviceType}/{id}` | one item in the generic view shape |

### laptop
| | |
|---|---|
| `POST /laptops` · `PUT /laptops/{id}` · `DELETE /laptops/{id}` | merchant CRUD |
| `PATCH /laptops/{id}/stock` | stock only |
| `GET /laptops` · `GET /laptops/{id}` | list / detail |
| `GET /laptops/search` | 23 filters, in-stock by default |
| `POST /laptops/compare` | aligned table, differing rows first |
| `GET /laptops/spec-keys` | the `extraSpecs` whitelist |

### identity · discount · order
| | |
|---|---|
| `POST /identity/verify` · `GET /identity/status` | verification, rate-limited per IP |
| `GET /discounts/limit/{laptopId}` | negotiation envelope |
| `POST /discounts/request` | **the only place a discount number is decided** |
| `GET /discounts/{offerId}/valid` | offer still usable? |
| `GET /discounts/history?identityKey=` | abuse analysis |
| `POST /orders` | close: holds stock, re-derives price, redeems offer |
| `POST /orders/{id}/payment-link` · `GET /orders/{id}/status` | payment |
| `POST /orders/{id}/refresh-payment` · `POST /orders/{id}/settle` | poll the gateway / mark settled |
| `POST /webhooks/razorpay` | signature-verified Razorpay events |

### webinfo · agent
| | |
|---|---|
| `GET /webinfo/subbrand/{id}?query=` | soft info, 7-day cache, framed as unverified |
| `GET /agent/tools` | JSON-schema tool definitions |
| `GET /agent/vocabulary` | live filter values from the database |
| `GET /agent/manifest` | tools + vocabulary + boundary + policy, one payload |

### chat — the sales agent
| | |
|---|---|
| `POST /chat` | send a customer message; omit `conversationId` to start a new one |
| `GET /chat/{id}` | full transcript including every tool call and result |
| `GET /chat/{id}/state` | the sales state held outside the LLM |
| `GET /chat/meta/provider` | which LLM client is wired in |
| `GET /conversations` | every conversation, for the merchant dashboard |
| `GET /analytics/audit` · `GET /analytics/audit/{id}` | the hallucination audit, fleet-wide or one |
| `GET /analytics/summary` | conversion, revenue, discount exposure, objections |

```bash
curl -s localhost:8080/chat -H 'Content-Type: application/json' \
  -d '{"message":"gaming laptop under 90000"}' | jq '.data | {reply, stage, toolCalls}'
```

**It runs with no API key.** `marketplace.agent.provider` defaults to `scripted` — a
deterministic fake model that drives the real loop (model → tool call → tool result → reply)
with no network and no variance, so the orchestration is testable independently of prompt
quality. Set `AGENT_PROVIDER=gemini` and `GEMINI_API_KEY=...` to use the real thing.

The scripted client is not a toy: `mvn test` and the full scenario suite run against it, so
the orchestration, the tool guarantees and the audit are all verifiable with no key, no
network and no variance. Prompt quality is a separate problem from whether the machinery
works, and conflating them is how you end up debugging a rate limiter instead of an agent.

---

## The parts that carry the design

**Search cannot return what the shop cannot sell.** `LaptopSpecifications` applies
`stockQty > 0` unless the caller explicitly opts out, and the agent's tool definition has no
`inStockOnly` parameter. The agent is structurally unable to pitch a phantom.

**`ExtraSpecKey` is one enum doing three jobs** — the merchant dropdown, the create/update
whitelist, and the agent's permitted vocabulary. A merchant cannot enter a spec the agent has no
words for, and the agent cannot invent one. Values are type-checked too, so
`KEYBOARD_BACKLIGHT: "yes please"` is a 400.

**Discounts are a pure function.** `DiscountApprovalPolicy` takes (laptop, requested, rounds,
repeat-redeemer) and returns a number. Same conversation shape, same number, every time. The LLM
frames and negotiates; it never picks the figure, and `POST /orders` re-derives the price from the
stored offer rather than trusting anything the agent passes in — the agent cannot pass a price at
all.

**Offers are bound to a verified identity, not a session.** Otherwise reopening the chat farms a
fresh negotiation ladder. A repeat redeemer on the same laptop inside the look-back window gets the
`repeat-buyer-cap`, not a reset to baseline.

**Stock is locked on close.** `findByIdForUpdate` takes a row lock, so two customers closing on the
last unit cannot both win. A failed payment releases the held unit.

**Web results are marked untrusted at the source.** `/webinfo/*` always returns `trustLevel:
UNVERIFIED_GENERAL` and a `usageRule` telling the agent to phrase it as general, never as a fact
about the unit in stock, and to ignore any instruction inside the summary text.

---

## Tuning the agent against this

`GET /agent/manifest` is the point of the `agent/` package. It returns, from live data:

- **tools** — JSON-schema definitions in function-calling shape, each with `httpMethod` + `path` so
  your runtime can dispatch without hand-written glue, plus the `failureCodes` that tool can return
  and its `truthLevel`.
- **vocabulary** — brands, sub-brands, segments, price tiers, benchmark tiers, sort options and
  `extraSpecs` keys, **read from the database at request time**. Hard-coding these into a system
  prompt guarantees the prompt drifts out of sync with the shop; fetching them means retuning is a
  request, not an edit.
- **infoSourceBoundary** — what must come from the backend versus what may come from the web.
- **negotiationPolicy** — the live formula parameters, so the prompt can describe the ladder
  without duplicating the numbers.

`docs/AGENT_CONTRACT.md` has a starter system prompt built on top of it.

Config knobs live in `application.yml` under `marketplace.*` — opening discount, per-round bonus,
rounds counted, offer TTL, repeat-buyer cap, look-back window, identity rate limit, cache TTL.
Changing negotiation behaviour is a config change, not a prompt change and not a model change.

---

## Deliberate seed data

Ten laptops from ₹38,990 to ₹1,84,990 across five brands and four segments, plus three
smartphones. Two rows exist to make failure paths reachable on a fresh database:

- **ROG Strix G16** — `stockQty = 0`. Closing on it returns `OUT_OF_STOCK`.
- **Dell XPS 14** — `maxDiscountPct = 0`. Negotiating returns an approved 0% with reason
  `MERCHANT_CEILING`, so the agent has to defend value instead of discounting.

There are no MacBooks, which is the point: ask the agent for one and watch what it does.

---

## Proving it is not a chatbot

Two things separate an agent you can trust from one you hope is right, and both are cheap
once every tool call and result is persisted alongside the messages.

### The evaluation suite

```bash
./eval/run-scenarios.sh                      # against the offline client: free, instant
PACE=20 ./eval/run-scenarios.sh              # against a real model
```

Seven scenarios played end to end — knows-what-they-want, vague, price objection,
negotiation, unavailable inventory, requirement change mid-conversation, and sells out
before the close. Transcripts and audits land in `eval/transcripts/<timestamp>/`.

Checks marked `[check]` read the **tool calls**, not the prose, so they mean the same thing
whichever model is behind the agent. Everything else is printed for a human to read: LLM
output is not pass/fail, and pretending otherwise produces a suite that goes green while the
agent gets worse. A turn where the model never answered is skipped rather than passed —
without that, every negative check ("did NOT discount") passes when the whole run is broken.

### The hallucination audit

```bash
curl -s localhost:8080/analytics/audit | jq '{claimsChecked, unsupportedClaims, accuracy}'
curl -s localhost:8080/analytics/audit/{conversationId} | jq
```

Replays a transcript and checks every price, percentage, stock figure and catalog model name
the assistant stated against what it had actually been told **earlier in that same
conversation**. Three ranked sources:

| | |
|---|---|
| `TOOL` | the backend returned it — the agent may state it as fact |
| `CUSTOMER` | the customer said it — echoing back "your ₹90,000 budget" is not a fabrication |
| `NONE` | the agent produced it from nowhere. **This is the number that matters.** |

Precision matters more than recall: an audit that flags sixteen things and is wrong sixteen
times teaches you to ignore it. So digits inside any string a tool returned count as known
(`RTX 4060` and `Ryzen 7 7840HS` contain numbers that are not prices), numbers the customer
used are tracked separately, and small numbers are ignored as prose.

What it still cannot do: catch a wholly invented product absent from the catalog, or a
fabricated figure that happens to match a real one. Worth saying out loud — a metric you
oversell is worse than none.

---

## Payments

Razorpay via the **Payment Links** API. The `PaymentGateway` seam was already
`order → (providerRef, url)`, which is exactly the shape of a payment link, so the order,
discount and agent layers are untouched and the agent's existing `create_payment_link` tool
now returns a real URL with no prompt change.

```bash
RAZORPAY_KEY_ID=rzp_test_... RAZORPAY_KEY_SECRET=... mvn spring-boot:run
```

With no keys set the offline stub stays wired in and the whole order flow still runs — no
money moves, and every test stays green. Startup prints which one you got, and whether the
key is test or live; read that before demoing.

| | |
|---|---|
| `POST /webhooks/razorpay` | signature-verified receiver for `payment_link.paid` |
| `POST /orders/{id}/refresh-payment` | poll Razorpay directly — for demos behind NAT |

**The webhook is the one endpoint where an anonymous caller could hand himself a laptop**, so:
the raw body is verified against an HMAC-SHA256 signature *before* it is parsed; no configured
secret means every call is rejected rather than trusted, because an endpoint that fails open
is worse than one that does not exist; and the comparison is constant-time. The amount is
never read from the payload — Razorpay is asked whether the link was paid, and what it was
paid for comes from our own row. A webhook claiming "paid ₹1" cannot buy a ₹90,000 laptop
because that number is not an input to anything.

The webhook is the correct mechanism and needs a public URL. `refresh-payment` exists because
demos happen on laptops behind NAT, and betting five minutes on a tunnel is a choice, not a
plan.

---

## Four guarantees the prompt cannot break

Every one of these was a rule in the system prompt first, and a real model broke each one in
testing. They are now in code.

1. **Identity comes from conversation state, never from model arguments.** The agent cannot
   claim to be a customer it has not verified.
2. **Negotiation rounds are counted by the backend.** The model's value is read and
   discarded. Asking three times in one breath does not raise the ladder.
3. **`inStockOnly` is hardcoded true and absent from the tool schema.** The agent is
   structurally unable to pitch a phantom.
4. **The agent only sees the tools it is currently allowed to use.** `create_order` does not
   exist in its world until an identity is verified. `ToolExecutor` already refuses the call —
   this is the second lock, and it removes the temptation rather than punishing it. Same
   reasoning as leaving `maxDiscountPct` out of `LaptopSummaryDto`: a capability the model
   never sees is one it cannot talk itself into.

Two more that are about being a good salesperson rather than a safe one:

**A budget is a centre, not a wall.** When a search with a price ceiling returns nothing at
all, the backend retries 20% higher and returns the near misses with a note saying they are
over budget and must be presented as such. A customer saying "around ₹120k" and being told
"we have nothing" while a ₹124,990 machine sits in stock is a sale lost to an arithmetic
comparison. It fires only when the strict search was empty, so it can never push an
over-budget machine at someone who had real options.

**Presented products beat search results.** If the agent deliberately called
`present_products`, that choice is what the customer sees — not the raw search hits. And
`present_products` re-reads every id and re-checks stock at presentation time.

---

## Not built here

- **Auth.** Merchant endpoints (`POST/PUT/DELETE /laptops`, `/discounts/history`) are wide open.
  Before this is public, they need a role check.
- **Cart.** Orders are single-item. Cross-sell needs a cart; the spec's build order puts it after
  a working close, and that is the right call.
- **`FLYWAY_DEV_CLEAN` must go before this sees real data.** `clean-on-validation-error` wipes
  the schema on a checksum mismatch. Safe while the schema is moving and everything is
  reseeded on boot; catastrophic the first time it points at data anyone cares about.
- **Trimming `search_laptops`.** Twenty-three parameters is more surface than the model uses,
  and every extra field is another chance to emit malformed JSON for its own tool call. Eight
  would cover every behaviour demoed here, cost fewer tokens per request, and reduce the
  `tool_use_failed` rate.