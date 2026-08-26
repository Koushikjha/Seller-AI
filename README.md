# Seller AI

An autonomous AI sales agent that behaves like a shopkeeper — discovers needs, handles objections, negotiates within merchant-set limits, and closes the sale. The LLM runs the conversation; a Spring Boot backend owns every price, spec and stock figure, so the model cannot invent one.

Source of truth for the AI sales agent. Java 21 · Spring Boot 3.3 · MySQL · Flyway · Maven.

No LLM logic lives here. The agent service calls these endpoints as tools; this service decides
whether what the agent wants to do is actually allowed.

> **The AI controls sales strategy and conversation. The backend controls business truth and execution.**

---

## Run it

```bash
docker compose up -d          # Postgres on :5432
mvn spring-boot:run           # app on :8080, Flyway migrates, seed data loads
```

Then:

```bash
curl localhost:8080/laptops/search?maxPrice=90000\&discreteGpuRequired=true | jq
curl localhost:8080/agent/manifest | jq
open http://localhost:8080/swagger-ui.html
```

Tests run against in-memory H2 — no database needed:

```bash
mvn test
```

> **Run `mvn test` first.** The build has not been compiled in the environment it was written in
> (no Maven Central access there), so treat the first green test run as the real acceptance gate.
> The one thing worth watching is the `extra_specs` JSON mapping (`@JdbcTypeCode(SqlTypes.JSON)`)
> under H2; if it misbehaves there, the fallback is an `AttributeConverter<Map,String>` plus a
> `TEXT` column — nothing queries inside that JSON.

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
├── agent/           tool manifest + live vocabulary for the LLM layer
├── common/          ApiResponse envelope, exceptions, global handler
└── config/          MarketplaceProperties, CORS, seed loader
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
| `POST /orders/{id}/payment-link` · `POST /orders/{id}/settle` · `GET /orders/{id}/status` | payment |

### webinfo · agent
| | |
|---|---|
| `GET /webinfo/subbrand/{id}?query=` | soft info, 7-day cache, framed as unverified |
| `GET /agent/tools` | JSON-schema tool definitions |
| `GET /agent/vocabulary` | live filter values from the database |
| `GET /agent/manifest` | tools + vocabulary + boundary + policy, one payload |

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

## Not built here

- **Razorpay.** `PaymentGateway` is the seam and the default stub keeps the whole order flow
  runnable with no account. A Razorpay implementation is one class plus a bean; nothing in the
  order, discount or agent layers changes. `POST /orders/{id}/settle` stands in for the webhook.
- **Auth.** Merchant endpoints (`POST/PUT/DELETE /laptops`, `/discounts/history`) are wide open.
  Before this is public, they need a role check.
- **Cart.** Orders are single-item. Cross-sell needs a cart; the spec's build order puts it after
  a working close, and that is the right call.
