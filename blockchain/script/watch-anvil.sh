#!/usr/bin/env bash
# =====================================================================================================
# watch-anvil.sh — prints every new transaction of the local Anvil node, decoded with this project's ABI,
#                  followed by the registry storage it changed, in readable form
#
# Anvil cannot decode the registry by itself: a local node has no ABI and no source verification, so its
# --print-traces output shows raw selectors (`9fed4fbc(…)`) and raw storage slots (`@ 0x6f6e…: 0 → 0x…`).
# This script polls the node and, for each new transaction:
#   1. replays it with `cast run --la` (local artifacts of this project): called function, arguments, events;
#   2. reads, through the registry getters, every entry the call writes — at the parent block and at the
#      transaction's block — and prints each field that changed: its readable value (amounts in €, dates in UTC,
#      ids as UUIDs, the payment reference as text) and, dimmed, its hexadecimal value.
#      With --verbose (slow), each field also shows where it lives (slot — the one Anvil's storage changes show —,
#      byte offset, size, from the compiled layout), the raw 32-byte word before → after and, for a field packed
#      with others in one word, its own bytes.
# Works with the docker compose node or an Anvil started by hand (it only talks to the RPC). Reading the
# "before" state needs the parent block's state: the compose node keeps it (--preserve-historical-states).
#
# USAGE (WSL)
#   cd blockchain
#   ./script/watch-anvil.sh               # transactions mined from now on (Ctrl+C to stop)
#   ./script/watch-anvil.sh --from 1      # also decode the history, from block 1
#   ./script/watch-anvil.sh --verbose     # + slot, byte offset, size and raw storage words of each field (slow)
#
# ENVIRONMENT
#   RPC_URL   default http://localhost:8545
# =====================================================================================================
set -euo pipefail

RPC_URL="${RPC_URL:-http://localhost:8545}"
CAST="$(command -v cast || echo "$HOME/.foundry/bin/cast")"
FORGE="$(command -v forge || echo "$HOME/.foundry/bin/forge")"
ZERO32="0x0000000000000000000000000000000000000000000000000000000000000000"
ZERO20="0x0000000000000000000000000000000000000000"

FROM=""; VERBOSE=false
while [ $# -gt 0 ]; do
  case "$1" in
    --from)    FROM="${2:-}"; shift ;;
    --verbose) VERBOSE=true ;;
    *) echo "Usage: $0 [--from <block>] [--verbose]" >&2; exit 2 ;;
  esac
  shift
done
[ -z "$FROM" ] || [[ "$FROM" =~ ^[0-9]+$ ]] || { echo "--from expects a block number" >&2; exit 2; }

# Always run from blockchain/: `cast run --la` and `forge inspect` read the artifacts of this project.
cd "$(dirname "$0")/.."
[ -x "$CAST" ] || { echo "cast not found (install Foundry: foundryup). On Windows, run inside WSL." >&2; exit 1; }
command -v jq >/dev/null || { echo "jq not found (sudo apt install jq)" >&2; exit 1; }
"$CAST" chain-id --rpc-url "$RPC_URL" >/dev/null || { echo "no node answers on $RPC_URL" >&2; exit 1; }
# Artifacts used by --la; lint warnings silenced, a failed build still stops the script.
"$FORGE" build >/dev/null 2>&1 || { echo "forge build failed — run it to see why" >&2; exit 1; }

# Storage slot of each registry variable, from the compiled layout (never hard-coded).
LAYOUT="$("$FORGE" inspect CommonLinkRegistry storageLayout --json 2>/dev/null)"
var_slot() { jq -r --arg l "$1" '.storage[] | select(.label == $l) | .slot' <<< "$LAYOUT"; }
SLOT_ASSOCIATIONS="$(var_slot associations)"
SLOT_CAMPAIGNS="$(var_slot campaigns)"
SLOT_DONATIONS="$(var_slot donations)"
SLOT_DONOR_STATS="$(var_slot donorStats)"
SLOT_DONOR_CAMPAIGN="$(var_slot donorCampaignTotal)"
SLOT_PAYOUTS="$(var_slot payouts)"
SLOT_CAMPAIGN_STATS="$(var_slot campaignStats)"

# Registry writes, by selector.
SIG_REGISTER_ASSOCIATION="registerAssociation(address,bytes32)"
SIG_REGISTER_CAMPAIGN="registerCampaign(bytes32,address,bytes32)"
SIG_UPDATE_BUDGET="updateCampaignBudget(bytes32,bytes32)"
SIG_RECORD_DONATION="recordDonation(bytes32,address,bytes32,uint96,bytes32,bytes32)"
SIG_RECORD_PAYOUT="recordPayout(bytes32,bytes32,uint96,bytes32)"
SEL_REGISTER_ASSOCIATION="$("$CAST" sig "$SIG_REGISTER_ASSOCIATION")"
SEL_REGISTER_CAMPAIGN="$("$CAST" sig "$SIG_REGISTER_CAMPAIGN")"
SEL_UPDATE_BUDGET="$("$CAST" sig "$SIG_UPDATE_BUDGET")"
SEL_RECORD_DONATION="$("$CAST" sig "$SIG_RECORD_DONATION")"
SEL_RECORD_PAYOUT="$("$CAST" sig "$SIG_RECORD_PAYOUT")"
SEL_PAUSE="$("$CAST" sig "pause()")"
SEL_UNPAUSE="$("$CAST" sig "unpause()")"

# ── Readable values ───────────────────────────────────────────────────────────────────────────────
# fmt <type> <raw value>: eur (cents), date (Unix s), uuid (bytes32), text (bytes32), addr, hash, int, bool.
fmt() {
  local t="$1" v="$2"
  case "$t" in
    eur)  printf "%'d,%02d €" "$((v / 100))" "$((v % 100))" ;;
    date) if [ "$v" = 0 ]; then echo "—"; else date -u -d "@$v" '+%Y-%m-%d %H:%M:%S UTC'; fi ;;
    uuid) if [ "$v" = "$ZERO32" ]; then echo "—"; else local h="${v#0x}"; echo "${h:0:8}-${h:8:4}-${h:12:4}-${h:16:4}-${h:20:12}"; fi ;;
    text) if [ "$v" = "$ZERO32" ]; then echo "—"; else "$CAST" parse-bytes32-string "$v" 2>/dev/null || echo "$v"; fi ;;
    addr) if [ "$v" = "$ZERO20" ]; then echo "—"; else echo "$v"; fi ;;
    hash) if [ "$v" = "$ZERO32" ]; then echo "—"; else echo "$v"; fi ;;
    *)    echo "$v" ;;
  esac
}

# to_hex <raw getter value>: the value in hexadecimal (numbers converted, true/false → 0x1/0x0, hex kept as is).
to_hex() {
  local v="$1"
  case "$v" in
    0x*)   echo "$v" ;;
    true)  echo "0x1" ;;
    false) echo "0x0" ;;
    *)     if [ "${#v}" -le 18 ]; then printf '0x%x\n' "$v"; else "$CAST" to-hex "$v"; fi ;;
  esac
}

# read_at <block> <getter signature with flat return types> <args…>: one raw value per line ("150000 [1.5e5]" → 150000).
# The hub's get* getters return structs of static fields only, whose ABI encoding is the flat list of the fields: the
# raw return data is decoded against the flat types, so "getCampaign(bytes32)(address,uint96,uint32,bytes32)" yields
# one line per field.
read_at() {
  local block="$1" sig="$2"; shift 2
  local call="${sig%%)*})" ret="${sig#*)}" raw
  raw="$("$CAST" call "$REGISTRY" "$call" "$@" --block "$block" --rpc-url "$RPC_URL" 2>/dev/null)" || return 0
  "$CAST" abi-decode "f()$ret" "$raw" 2>/dev/null | awk '{print $1}'
}

# member_info <variable> <field>: "<slot offset in the entry> <byte offset in that slot> <size in bytes>", from the
# compiled layout. A field that is not a struct member (the entry is a plain value) sits at offset 0 of the entry.
member_info() {
  jq -r --arg v "$1" --arg f "$2" '
    .types as $ty
    | def leaf($x): if $ty[$x].encoding == "mapping" then leaf($ty[$x].value) else $x end;
    leaf(.storage[] | select(.label == $v) | .type) as $l
    | ((($ty[$l].members // []) | map(select(.label == $f)) | .[0]) // {slot: "0", offset: 0, type: $l})
    | "\(.slot) \(.offset) \($ty[.type].numberOfBytes)"' <<< "$LAYOUT"
}

# slot_plus <slot> <n>: the 32-byte slot n positions after <slot> (struct members follow the entry's base slot).
slot_plus() { python3 -c "import sys; print(format(int(sys.argv[1], 16) + int(sys.argv[2]), '#066x'))" "$1" "$2"; }

# field_bytes <word> <byte offset> <size>: the bytes of a packed field inside a 32-byte word. Offsets count from the
# right (low-order) end, as Solidity packs them.
field_bytes() {
  local hex="${1#0x}"
  [ "${#hex}" -eq 64 ] || { echo "?"; return; }
  echo "0x${hex:$(( (32 - $2 - $3) * 2 )):$(( $3 * 2 ))}"
}

# raw_at <block> <slot>: the raw 32-byte word stored in that registry slot at that block.
raw_at() { "$CAST" storage "$REGISTRY" "$2" --block "$1" --rpc-url "$RPC_URL" 2>/dev/null || echo "?"; }

# show <label> <variable> <base slot> <getter> <"field:type field:type …"> <getter args…>
# Prints the entry's fields whose value differs between the parent block and the transaction's block: the readable
# value and its hex value (dimmed). With --verbose instead of the hex value: where the field lives — slot, byte
# offset counted from the right of the word as Solidity packs it, size — and the raw content of that slot
# before → after, exactly as Anvil's storage changes show it.
show() {
  local label="$1" var="$2" base="$3" getter="$4" fields="$5"; shift 5
  local -a names types before after
  local f
  for f in $fields; do names+=("${f%%:*}"); types+=("${f##*:}"); done
  mapfile -t before < <(read_at "$((BLOCK - 1))" "$getter" "$@")
  mapfile -t after < <(read_at "$BLOCK" "$getter" "$@")
  if [ "$VERBOSE" = true ]; then
    printf '    %s   \033[2m(base slot %s)\033[0m\n' "$label" "$base"
  else
    printf '    %s\n' "$label"
  fi
  if [ "${#after[@]}" -ne "${#names[@]}" ]; then echo "      (cannot read this entry)"; return; fi
  local i changed=false member off size slot hex
  for i in "${!names[@]}"; do
    if [ "${#before[@]}" -eq "${#names[@]}" ] && [ "${before[$i]}" = "${after[$i]}" ]; then continue; fi
    changed=true
    # Default: the field's hex value, dimmed (left out for addresses and hashes, already printed in hex).
    hex=""
    if [ "$VERBOSE" = false ] && [ "${types[$i]}" != addr ] && [ "${types[$i]}" != hash ]; then
      if [ "${#before[@]}" -ne "${#names[@]}" ]; then
        hex="$(printf '   \033[2m(%s)\033[0m' "$(to_hex "${after[$i]}")")"
      else
        hex="$(printf '   \033[2m(%s → %s)\033[0m' "$(to_hex "${before[$i]}")" "$(to_hex "${after[$i]}")")"
      fi
    fi
    if [ "${#before[@]}" -ne "${#names[@]}" ]; then
      printf '      %-16s %s%s\n' "${names[$i]}" "$(fmt "${types[$i]}" "${after[$i]}")" "$hex"
    else
      printf '      %-16s %s → %s%s\n' "${names[$i]}" "$(fmt "${types[$i]}" "${before[$i]}")" "$(fmt "${types[$i]}" "${after[$i]}")" "$hex"
    fi
    [ "$VERBOSE" = true ] || continue
    # --verbose: where the field lives and the raw words (slow: one layout lookup and two storage reads per field).
    read -r member off size <<< "$(member_info "$var" "${names[$i]}")"
    slot="$(slot_plus "$base" "$member")"
    printf '      \033[2m%-16s slot %s · byte offset %s · %s bytes\033[0m\n' "" "$slot" "$off" "$size"
    local wb wa
    wb="$(raw_at "$((BLOCK - 1))" "$slot")"; wa="$(raw_at "$BLOCK" "$slot")"
    printf '      \033[2m%-16s %s → %s\033[0m\n' "" "$wb" "$wa"
    # A field smaller than 32 bytes shares its word: show its own bytes (Solidity packs from the right).
    if [ "$size" -lt 32 ]; then
      printf '      \033[2m%-16s field bytes %s → %s\033[0m\n' "" "$(field_bytes "$wb" "$off" "$size")" "$(field_bytes "$wa" "$off" "$size")"
    fi
  done
  [ "${#before[@]}" -eq "${#names[@]}" ] || echo "      (state before this block unavailable: values after only)"
  [ "$changed" = true ] || echo "      (unchanged)"
}

# storage_of <tx>: the registry entries written by that call, before → after.
storage_of() {
  local tx="$1" input sel
  # `cast receipt … status` prints true / false (older Foundry: "1 (success)").
  [[ "$("$CAST" receipt "$tx" status --rpc-url "$RPC_URL")" =~ ^(true|1) ]] || { echo "  Storage: none (reverted)"; return; }
  REGISTRY="$("$CAST" tx "$tx" to --rpc-url "$RPC_URL")"
  input="$("$CAST" tx "$tx" input --rpc-url "$RPC_URL")"
  sel="${input:0:10}"
  local -a a
  case "$sel" in
    "$SEL_REGISTER_ASSOCIATION")
      mapfile -t a < <("$CAST" calldata-decode "$SIG_REGISTER_ASSOCIATION" "$input" | awk '{print $1}')
      echo "  Storage:"
      show "associations[${a[0]}]" associations "$("$CAST" index address "${a[0]}" "$SLOT_ASSOCIATIONS")" \
        "getAssociation(address)(bytes32,uint32)" "sirenHash:hash registeredAt:date" "${a[0]}"
      # The SIREN reverse index is internal (no getter since 3.0): its value is in the AssociationRegistered event.
      ;;
    "$SEL_REGISTER_CAMPAIGN"|"$SEL_UPDATE_BUDGET")
      local id; id="0x${input:10:64}"
      echo "  Storage:"
      show "campaigns[$(fmt uuid "$id")]" campaigns "$("$CAST" index bytes32 "$id" "$SLOT_CAMPAIGNS")" \
        "getCampaign(bytes32)(address,uint96,uint32,bytes32)" \
        "association:addr raised:eur registeredAt:date budgetHash:hash" "$id" ;;
    "$SEL_RECORD_DONATION")
      mapfile -t a < <("$CAST" calldata-decode "$SIG_RECORD_DONATION" "$input" | awk '{print $1}')
      local don="${a[0]}" donor="${a[1]}" camp="${a[2]}"
      echo "  Storage:"
      show "donations[$(fmt uuid "$don")]" donations "$("$CAST" index bytes32 "$don" "$SLOT_DONATIONS")" \
        "getDonation(bytes32)(address,uint96,bytes32,uint32,bytes32,bytes32)" \
        "donor:addr amount:eur campaignId:uuid timestamp:date receiptHash:hash txRef:text" "$don"
      show "campaigns[$(fmt uuid "$camp")]" campaigns "$("$CAST" index bytes32 "$camp" "$SLOT_CAMPAIGNS")" \
        "getCampaign(bytes32)(address,uint96,uint32,bytes32)" \
        "association:addr raised:eur registeredAt:date budgetHash:hash" "$camp"
      show "donorStats[$donor]" donorStats "$("$CAST" index address "$donor" "$SLOT_DONOR_STATS")" \
        "getDonorStats(address)(uint128,uint32,uint32,uint32)" \
        "totalDonated:eur donationCount:int uniqueCampaigns:int firstDonationAt:date" "$donor"
      show "donorCampaignTotal[$donor][$(fmt uuid "$camp")]" donorCampaignTotal \
        "$("$CAST" index bytes32 "$camp" "$("$CAST" index address "$donor" "$SLOT_DONOR_CAMPAIGN")")" \
        "getDonorCampaignTotal(address,bytes32)(uint128)" "total:eur" "$donor" "$camp"
      show "campaignStats[$(fmt uuid "$camp")]" campaignStats "$("$CAST" index bytes32 "$camp" "$SLOT_CAMPAIGN_STATS")" \
        "getCampaignStats(bytes32)(uint96,uint32,uint32,uint32,uint32,uint32)" \
        "totalPaidOut:eur payoutCount:int donationCount:int uniqueDonors:int lastDonationAt:date lastPayoutAt:date" "$camp" ;;
    "$SEL_RECORD_PAYOUT")
      mapfile -t a < <("$CAST" calldata-decode "$SIG_RECORD_PAYOUT" "$input" | awk '{print $1}')
      local payout="${a[0]}" pcamp="${a[1]}"
      echo "  Storage:"
      show "payouts[$(fmt uuid "$payout")]" payouts "$("$CAST" index bytes32 "$payout" "$SLOT_PAYOUTS")" \
        "getPayout(bytes32)(bytes32,uint96,uint32,bytes32)" \
        "campaignId:uuid amount:eur timestamp:date txRef:text" "$payout"
      show "campaignStats[$(fmt uuid "$pcamp")]" campaignStats "$("$CAST" index bytes32 "$pcamp" "$SLOT_CAMPAIGN_STATS")" \
        "getCampaignStats(bytes32)(uint96,uint32,uint32,uint32,uint32,uint32)" \
        "totalPaidOut:eur payoutCount:int donationCount:int uniqueDonors:int lastDonationAt:date lastPayoutAt:date" "$pcamp" ;;
    "$SEL_PAUSE"|"$SEL_UNPAUSE")
      # Emergency pause (PAUSER_ROLE) / resume (UNPAUSER_ROLE or admin): the only state is OpenZeppelin's paused flag.
      echo "  Pause: paused() $(read_at "$((BLOCK - 1))" "paused()(bool)") → $(read_at "$BLOCK" "paused()(bool)")" ;;
    *) echo "  Storage: not a registry write this script knows (selector $sel)" ;;
  esac
}

NEXT="${FROM:-$(( $("$CAST" block-number --rpc-url "$RPC_URL") + 1 ))}"
echo "Watching $RPC_URL from block $NEXT (Ctrl+C to stop)"
while true; do
  # A node restart must not kill the watch: wait and retry.
  HEAD="$("$CAST" block-number --rpc-url "$RPC_URL" 2>/dev/null)" || { sleep 2; continue; }
  while [ "$NEXT" -le "$HEAD" ]; do
    BLOCK="$NEXT"
    for TX in $("$CAST" block "$NEXT" --json --rpc-url "$RPC_URL" | jq -r '.transactions[]'); do
      printf '\n\033[1m▶ block %s — tx %s\033[0m\n' "$NEXT" "$TX"
      OUT="$("$CAST" run "$TX" --rpc-url "$RPC_URL" --la 2>&1)" || true
      if grep -q '^Traces:' <<< "$OUT"; then
        # Keep the decoded call only (cast first prints the replay / compilation progress, then a summary).
        sed -n '/^Traces:/,/^Transaction successfully executed\./p' <<< "$OUT" | sed '$d'
      elif grep -q 'BlockOutOfRange' <<< "$OUT"; then
        # A block mined before a node restart whose parent state was not dumped (the compose node keeps it
        # with --preserve-historical-states, but only for blocks mined since that flag was set).
        echo "  cannot replay: the node no longer holds the state before this block (mined before a restart)"
      else
        echo "  cannot replay: $(grep -m1 'Error' <<< "$OUT" | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-200)"
      fi
      storage_of "$TX" || echo "  Storage: could not be read"
    done
    NEXT=$((NEXT + 1))
  done
  sleep 1
done
