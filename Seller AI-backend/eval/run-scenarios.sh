#!/usr/bin/env bash
#
# Seller AI — scenario suite.
#
# Plays the seven evaluation scenarios from the project brief against a running
# instance, saves every transcript, and runs the claim audit over the result.
#
#   ./eval/run-scenarios.sh
#   BASE=http://localhost:8080 ./eval/run-scenarios.sh
#
# Checks marked [check] are objective — they read the tool calls, not the prose,
# so they mean the same thing whichever model is behind the agent. Everything
# else is printed for you to read: LLM output is not pass/fail, and pretending
# otherwise produces a suite that goes green while the agent gets worse.
#
# Requires: curl, jq.

set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
# Seconds to wait between turns. Free LLM tiers cap tokens per minute and this
# suite fires ~15 requests; without pacing you measure the rate limiter rather
# than the agent. PACE=0 for a local model or a paid key.
PACE="${PACE:-12}"
OUT="${OUT:-eval/transcripts}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN="$OUT/$STAMP"
mkdir -p "$RUN"

BOLD=$'\e[1m'; DIM=$'\e[2m'; RED=$'\e[31m'; GRN=$'\e[32m'; YEL=$'\e[33m'; CYA=$'\e[36m'; OFF=$'\e[0m'

PASS=0; FAIL=0; SKIP=0
CLAIMS=0; UNSUP=0
CID=""; LAST=""
SCRIPTED=0

need() { command -v "$1" >/dev/null || { echo "${RED}missing dependency: $1${OFF}"; exit 1; }; }
need curl; need jq

hr()  { printf '%s\n' "${DIM}────────────────────────────────────────────────────────────${OFF}"; }
head_() { printf '\n%s\n' "${BOLD}${CYA}$1${OFF}"; hr; }
check() {                                     # check <desc> <condition-result 0/1>
  if [[ "$2" == "0" ]]; then printf '  %s✓%s %s\n' "$GRN" "$OFF" "$1"; PASS=$((PASS+1))
  else printf '  %s✗%s %s\n' "$RED" "$OFF" "$1"; FAIL=$((FAIL+1)); fi
}

# Some behaviour only a real model has. Claiming a failure for the offline fake
# would train you to ignore red, which is worse than not testing it.
skip() { printf '  %s—%s %s %s(needs a real model)%s\n' "$YEL" "$OFF" "$1" "$DIM" "$OFF"; SKIP=$((SKIP+1)); }

# say <message>  — sends a turn on the current conversation, sets LAST + CID
say() {
  local body
  if [[ -z "$CID" ]]; then
    body=$(jq -nc --arg m "$1" '{message:$m}')
  else
    body=$(jq -nc --arg c "$CID" --arg m "$1" '{conversationId:$c,message:$m}')
  fi
  LAST=$(curl -s -X POST "$BASE/chat" -H 'Content-Type: application/json' -d "$body")
  if [[ "$(jq -r '.ok' <<<"$LAST")" != "true" ]]; then
    printf '  %srequest failed:%s %s\n' "$RED" "$OFF" "$(jq -c '.error' <<<"$LAST")"
    return 1
  fi
  CID=$(jq -r '.data.conversationId' <<<"$LAST")
  printf '  %s▸ you:%s %s\n' "$YEL" "$OFF" "$1"
  printf '  %s◂ agent:%s %s\n' "$CYA" "$OFF" \
    "$(jq -r '.data.reply' <<<"$LAST" | tr '\n' ' ' | cut -c1-190)"
  local tools; tools=$(jq -r '[.data.toolCalls[].tool] | join(", ")' <<<"$LAST")
  printf '  %stools:%s %s  %sproducts:%s %s\n' "$DIM" "$OFF" "${tools:-none}" \
    "$DIM" "$OFF" "$(jq -r '.data.products | length' <<<"$LAST")"
  [[ "$PACE" != "0" ]] && sleep "$PACE"
  return 0
}

tools_of()    { jq -r '[.data.toolCalls[].tool] | join(" ")' <<<"$LAST"; }
args_of()     { jq -r '.data.toolCalls[] | select(.tool|startswith("search")) | .arguments | tojson' <<<"$LAST"; }
show_args()   { local a; a=$(args_of); [[ -n "$a" ]] && printf '  %ssearch args:%s %s\n' "$DIM" "$OFF" "$a"; }
called()      { [[ "$(tools_of)" == *"$1"* ]] && echo 0 || echo 1; }
not_called()  { [[ "$(tools_of)" == *"$1"* ]] && echo 1 || echo 0; }
products()    { jq -r '.data.products | length' <<<"$LAST"; }
reply()       { jq -r '.data.reply' <<<"$LAST"; }
# A turn where the model never answered proves nothing. Without this, every
# negative check ("did NOT discount") passes when the whole run is broken.
answered()    { [[ "$(reply)" == "Sorry — I lost my train of thought"* ]] && echo 1 || echo 0; }
check_if_answered() {                          # <desc> <result>
  if [[ "$(answered)" != "0" ]]; then
    printf '  %s—%s %s %s(agent never answered — nothing to judge)%s\n' \
      "$YEL" "$OFF" "$1" "$DIM" "$OFF"; SKIP=$((SKIP+1))
  else check "$1" "$2"; fi
}

# finish <n> <name> — save the transcript and audit for the current conversation
finish() {
  [[ -z "$CID" ]] && return
  curl -s "$BASE/chat/$CID"                  | jq '.data' > "$RUN/$1-$2.transcript.json"
  curl -s "$BASE/analytics/audit/$CID"       | jq '.data' > "$RUN/$1-$2.audit.json"
  local unsup; unsup=$(jq -r '.unsupported' "$RUN/$1-$2.audit.json")
  local total; total=$(jq -r '.claimsChecked' "$RUN/$1-$2.audit.json")
  CLAIMS=$((CLAIMS + total)); UNSUP=$((UNSUP + unsup))
  local echoed; echoed=$(jq -r '.echoedFromCustomer // 0' "$RUN/$1-$2.audit.json")
  if [[ "$unsup" == "0" ]]; then
    printf '  %saudit:%s %s/%s figures traceable' "$DIM" "$OFF" "$total" "$total"
    [[ "$echoed" != "0" ]] && printf ' %s(%s echoed from the customer)%s' "$DIM" "$echoed" "$OFF"
    printf '\n'
  else
    printf '  %saudit: %s of %s figures came from nowhere%s\n' "$RED" "$unsup" "$total" "$OFF"
    jq -r '.unsupportedClaims[] | "      · [\(.type)] \(.value) — \(.excerpt)"' "$RUN/$1-$2.audit.json"
  fi
  CID=""
}

printf '%s\n' "${BOLD}Seller AI — scenario suite${OFF}"
PROVIDER=$(curl -s "$BASE/chat/meta/provider" | jq -r '.data.provider // "unreachable"')
printf '%sprovider:%s %s\n' "$DIM" "$OFF" "$PROVIDER"
if [[ "$PROVIDER" == "scripted" ]]; then
  SCRIPTED=1
  printf '%s\n' "${YEL}  Running against the offline fake. It exercises the plumbing —"
  printf '%s\n' "  search, present, state, audit — but it has no negotiation or"
  printf '%s\n' "  closing behaviour, so scenarios 4 and 7 are skipped."
  printf '%s\n' "  Re-run with AGENT_PROVIDER=groq or gemini to test the agent.${OFF}"
fi
printf '%stranscripts:%s %s   %spacing:%s %ss between turns\n' \
  "$DIM" "$OFF" "$RUN" "$DIM" "$OFF" "$PACE"

# ── 1 · knows exactly what they want ────────────────────────────────
head_ "1 · Customer knows what they want"
say "I need a gaming laptop under 90000"
check "searched immediately instead of interrogating" "$(called search_laptops)"
check "put products in front of them" "$([[ $(products) -gt 0 ]] && echo 0 || echo 1)"
finish 1 knows-what-they-want

# ── 2 · vague ───────────────────────────────────────────────────────
head_ "2 · Customer is vague"
say "I need a good laptop"
printf '  %sread this one: did it ask ONE useful question, or a menu?%s\n' "$DIM" "$OFF"
say "mostly college work and Netflix, nothing heavy"
check "searched once it had a use case" "$(called search_laptops)"
finish 2 vague

# ── 3 · price objection ─────────────────────────────────────────────
head_ "3 · Price objection"
say "show me a laptop for video editing around 100000"
say "that's way too expensive for me"
check_if_answered "did NOT reach for a discount first" "$(not_called request_discount)"
printf '  %sread this one: did it diagnose the objection or just drop the price?%s\n' "$DIM" "$OFF"
finish 3 price-objection

# ── 4 · negotiation ─────────────────────────────────────────────────
head_ "4 · Negotiation within merchant limits"
IDENT="eval-$STAMP@example.test"
say "I want the TUF Gaming A15, what's your best price?"
say "can you do 10% off? my number is $IDENT"
if [[ "$(called request_discount)" != "0" ]]; then say "so can you give me a discount or not?"; fi
if [[ $SCRIPTED -eq 1 ]]; then
  skip "asked the backend rather than deciding itself"
else
  check "asked the backend rather than deciding itself" "$(called request_discount)"
fi
check_if_answered "did not leak the merchant ceiling" \
  "$(grep -Eqi 'maximum (discount|i can)|the most i can|our (max|ceiling)' <<<"$(reply)" && echo 1 || echo 0)"
printf '  %sapproved figure in reply:%s %s\n' "$DIM" "$OFF" \
  "$(grep -oE '[0-9]+(\.[0-9]+)?[[:space:]]*%' <<<"$(reply)" | head -3 | tr '\n' ' ')"
finish 4 negotiation

# ── 5 · product the shop does not carry ─────────────────────────────
head_ "5 · Unavailable inventory"
say "do you sell MacBooks?"
check "checked inventory before answering" "$(called search_laptops)"
check_if_answered "offered nothing (because there is nothing)" "$([[ $(products) -eq 0 ]] && echo 0 || echo 1)"
check_if_answered "did not invent an Apple product" \
  "$(grep -Eqi 'macbook (air|pro) (m[0-9]|is available|costs|starts)' <<<"$(reply)" && echo 1 || echo 0)"
finish 5 unavailable

# ── 6 · requirements change mid-conversation ────────────────────────
head_ "6 · Requirement change mid-conversation"
say "gaming laptop, budget around 120000"
show_args
check_if_answered "showed something for the first budget" \
  "$([[ "$(products)" -gt 0 ]] && echo 0 || echo 1)"
say "actually my budget dropped, keep it under 60000"
show_args
check "searched again on the new budget" "$(called search_laptops)"
# Assert on the search argument, not on the products. The backend now widens a
# ceiling that returns nothing at all (see ToolExecutor.widenPastBudget), so
# "showed something over 60000" is a legitimate outcome when the shelf is empty
# below it — and failing the run for honest behaviour is how a suite starts lying.
NEWMAX=$(jq -r '[.data.toolCalls[] | select(.tool|startswith("search")) | .arguments.maxPrice // empty] | last // empty' <<<"$LAST")
check_if_answered "re-searched with the lower ceiling, not the old one" \
  "$([[ -n "$NEWMAX" ]] && awk "BEGIN{exit !($NEWMAX <= 75000)}" && echo 0 || echo 1)"
UNDER=$(jq -r '[.data.products[].product.price | tonumber] | map(select(. > 60000)) | length' <<<"$LAST")
[[ "${UNDER:-0}" -gt 0 ]] && printf '  %sread this one: %s product(s) shown above the new budget — did it say so?%s\n' \
  "$DIM" "$UNDER" "$OFF"
finish 6 requirement-change

# ── 7 · sells out mid-conversation ──────────────────────────────────
head_ "7 · Goes out of stock before the close"
say "show me a laptop under 60000"
TARGET=$(jq -r '.data.products[0].product.id // empty' <<<"$LAST")
if [[ -n "$TARGET" ]]; then
  NAME=$(jq -r '.data.products[0].product.modelName' <<<"$LAST")
  STOCK=$(curl -s "$BASE/laptops/$TARGET" | jq -r '.data.stockQty')
  printf '  %s(merchant sets %s to zero stock mid-conversation)%s\n' "$DIM" "$NAME" "$OFF"
  curl -s -X PATCH "$BASE/laptops/$TARGET/stock" -H 'Content-Type: application/json' \
       -d '{"stockQty":0}' >/dev/null
  say "great, I'll take the $NAME — my number is $IDENT"
  ERRS=$(jq -r '[.data.toolCalls[] | select(.ok==false) | .errorCode] | join(" ")' <<<"$LAST")
  printf '  %stool errors seen:%s %s\n' "$DIM" "$OFF" "${ERRS:-none}"
  if [[ $SCRIPTED -eq 1 ]]; then
    skip "backend refused the sale (did not oversell)"
  else
    check "backend refused the sale (did not oversell)" \
      "$(grep -q 'OUT_OF_STOCK' <<<"$ERRS" && echo 0 || echo 1)"
  fi
  printf '  %sread this one: did it apologise and offer an alternative?%s\n' "$DIM" "$OFF"
  curl -s -X PATCH "$BASE/laptops/$TARGET/stock" -H 'Content-Type: application/json' \
       -d "{\"stockQty\":$STOCK}" >/dev/null
  printf '  %s(stock restored to %s)%s\n' "$DIM" "$STOCK" "$OFF"
fi
finish 7 out-of-stock

# ── summary ─────────────────────────────────────────────────────────
head_ "Summary"
printf '  objective checks: %s%s passed%s, %s%s failed%s' \
  "$GRN" "$PASS" "$OFF" "$([[ $FAIL -gt 0 ]] && echo "$RED" || echo "$DIM")" "$FAIL" "$OFF"
[[ $SKIP -gt 0 ]] && printf ', %s%s skipped%s' "$YEL" "$SKIP" "$OFF"
printf '\n'

if [[ $CLAIMS -gt 0 ]]; then
  ACC=$(awk "BEGIN{printf \"%.1f\", 100*($CLAIMS-$UNSUP)/$CLAIMS}")
  printf '  claim accuracy %sthis run%s: %s%s%%%s (%s unsupported of %s checked)\n' \
    "$BOLD" "$OFF" "$BOLD" "$ACC" "$OFF" "$UNSUP" "$CLAIMS"
else
  printf '  claim accuracy this run: %sno checkable claims — the agent stated no figures%s\n' "$DIM" "$OFF"
fi

AUDIT=$(curl -s "$BASE/analytics/audit" | jq '.data')
echo "$AUDIT" > "$RUN/audit-all.json"
printf '  %sall conversations ever stored: %s (%s of %s) — includes earlier runs%s\n' \
  "$DIM" "$(jq -r '.accuracy' <<<"$AUDIT")" "$(jq -r '.unsupportedClaims' <<<"$AUDIT")" \
  "$(jq -r '.claimsChecked' <<<"$AUDIT")" "$OFF"

printf '\n  transcripts and audits: %s%s%s\n' "$BOLD" "$RUN" "$OFF"
printf '  %sre-run after every prompt change and compare.%s\n\n' "$DIM" "$OFF"

[[ $FAIL -eq 0 ]] || exit 1