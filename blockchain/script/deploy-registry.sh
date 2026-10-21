#!/usr/bin/env bash
# =====================================================================================================
# deploy-registry.sh — executable version of blockchain/RUNBOOK.md §2–§3 (+ verification, §6 bookkeeping)
#
# Deploys CommonLinkRegistry v2 with script/DeployRegistry.s.sol, checks it on-chain, verifies the
# source code on the explorers and writes the public deployment record deployments/<network>.json.
#
# WHO RUNS IT: a human operator, on their own workstation (on Windows: inside WSL, where Foundry lives).
# Never on a server, never in CI: it needs the ADMIN private key.
#
# USAGE
#   cd blockchain
#   export ADMIN_ADDR=0x…  RECORDER_ADDR=0x…  ADMIN_PK=0x…  RPC_URL=https://…
#   ./script/deploy-registry.sh chiado            # Gnosis Chiado testnet (10200)
#   ./script/deploy-registry.sh gnosis            # Gnosis mainnet (100) — only after audit + Safe (sprint B8)
#   ./script/deploy-registry.sh anvil             # local Anvil node (31337), RPC_URL=http://localhost:8545
#
# OPTIONS
#   --dry-run       simulate the deployment (forge script without --broadcast): nothing is sent, no gas spent
#   --skip-tests    skip `forge test` (NOT recommended before a real broadcast)
#   --skip-verify   do not verify the source code on the explorers (can be done later, see §7 below)
#   --yes           do not ask for the interactive confirmation (scripted Anvil runs only)
#
# ENVIRONMENT (operator workstation only — never commit, never paste in a chat or a ticket)
#   ADMIN_ADDR         receives DEFAULT_ADMIN_ROLE (pause, setBadgeContract) — mainnet: handed to a Safe (B8)
#   RECORDER_ADDR      receives RECORDER_ROLE — the backend hot wallet (its key becomes ONCHAIN_RECORDER_PK)
#   ADMIN_PK           private key of ADMIN_ADDR, signs the deployment (never echoed by this script)
#   RPC_URL            RPC endpoint of the target chain (may embed a token: never echoed either)
#   ETHERSCAN_API_KEY  optional — Gnosisscan through the Etherscan V2 API (mainnet only, see §7)
#
# The script stops at the first error (set -e). It never prints a private key, a secret or the RPC URL.
# =====================================================================================================
set -euo pipefail

# ── 0. Arguments ──────────────────────────────────────────────────────────────────────────────────
NETWORK="${1:-}"
shift || true
DRY_RUN=false; SKIP_TESTS=false; SKIP_VERIFY=false; ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --dry-run)     DRY_RUN=true ;;
    --skip-tests)  SKIP_TESTS=true ;;
    --skip-verify) SKIP_VERIFY=true ;;
    --yes)         ASSUME_YES=true ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# Each network fixes its chain id and its Blockscout instance (no host is guessed elsewhere).
case "$NETWORK" in
  chiado) CHAIN_ID=10200; EXPLORER="https://gnosis-chiado.blockscout.com" ;;
  gnosis) CHAIN_ID=100;   EXPLORER="https://gnosis.blockscout.com" ;;
  anvil)  CHAIN_ID=31337; EXPLORER="" ;;
  *) echo "Usage: $0 <chiado|gnosis|anvil> [--dry-run] [--skip-tests] [--skip-verify] [--yes]" >&2; exit 2 ;;
esac

# ── Helpers ────────────────────────────────────────────────────────────────────────────────────────
FORGE="$(command -v forge || echo "$HOME/.foundry/bin/forge")"
CAST="$(command -v cast || echo "$HOME/.foundry/bin/cast")"
step() { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  ✓ %s\n' "$*"; }
die()  { printf '  ✗ %s\n' "$*" >&2; exit 1; }

# Always run from blockchain/ (paths below are relative to it).
cd "$(dirname "$0")/.."

# ── 1. Prerequisites: tools ───────────────────────────────────────────────────────────────────────
step "1. Tools"
[ -x "$FORGE" ] || die "forge not found (install Foundry: foundryup). On Windows, run this script inside WSL."
[ -x "$CAST" ]  || die "cast not found (install Foundry: foundryup)."
command -v jq >/dev/null || die "jq not found (sudo apt install jq) — used to read the broadcast file."
ok "$("$FORGE" --version | head -1)"

# ── 2. Prerequisites: environment (values are checked, never printed except public addresses) ─────
step "2. Environment"
for var in ADMIN_ADDR RECORDER_ADDR ADMIN_PK RPC_URL; do
  [ -n "${!var:-}" ] || die "$var is not set (see the header of this script)"
done
[[ "$ADMIN_ADDR" =~ ^0x[0-9a-fA-F]{40}$ ]]    || die "ADMIN_ADDR is not an address"
[[ "$RECORDER_ADDR" =~ ^0x[0-9a-fA-F]{40}$ ]] || die "RECORDER_ADDR is not an address"
[ "${ADMIN_ADDR,,}" != "${RECORDER_ADDR,,}" ] || die "RECORDER_ADDR must differ from ADMIN_ADDR (separation of powers)"
# The key must belong to ADMIN_ADDR (the deployer IS the admin). Compared without printing the key.
DERIVED="$("$CAST" wallet address --private-key "$ADMIN_PK")"
[ "${DERIVED,,}" = "${ADMIN_ADDR,,}" ] || die "ADMIN_PK does not match ADMIN_ADDR"
if [ "$NETWORK" != "anvil" ]; then
  [[ "$RPC_URL" == https://* ]] || die "RPC_URL must be https:// on a public network"
fi
ok "admin    : $ADMIN_ADDR"
ok "recorder : $RECORDER_ADDR"

# ── 3. Prerequisites: chain and balances ──────────────────────────────────────────────────────────
step "3. Chain ${NETWORK} (${CHAIN_ID})"
REMOTE_CHAIN_ID="$("$CAST" chain-id --rpc-url "$RPC_URL")"
[ "$REMOTE_CHAIN_ID" = "$CHAIN_ID" ] || die "RPC_URL answers chain id $REMOTE_CHAIN_ID, expected $CHAIN_ID"
ok "RPC answers chain id $REMOTE_CHAIN_ID"
ADMIN_BALANCE="$("$CAST" balance "$ADMIN_ADDR" --rpc-url "$RPC_URL" --ether)"
RECORDER_BALANCE="$("$CAST" balance "$RECORDER_ADDR" --rpc-url "$RPC_URL" --ether)"
ok "admin    balance: $ADMIN_BALANCE xDAI (pays the deployment)"
ok "recorder balance: $RECORDER_BALANCE xDAI (pays every future write — fund it from the faucet / treasury)"
[ "$("$CAST" balance "$ADMIN_ADDR" --rpc-url "$RPC_URL")" != "0" ] || die "the admin has no xDAI: fund it first (Chiado faucet, RUNBOOK §2)"

# ── 4. Build and test ─────────────────────────────────────────────────────────────────────────────
step "4. Build and tests"
"$FORGE" build
if [ "$SKIP_TESTS" = false ]; then
  "$FORGE" test
  ok "forge test passed"
else
  echo "  ! tests skipped (--skip-tests)"
fi

# ── 5. Confirmation (a broadcast spends gas and creates a PERMANENT, non-upgradeable contract) ───────
step "5. Confirmation"
if [ "$DRY_RUN" = true ]; then
  echo "  Dry run: the deployment is simulated, nothing is sent."
elif [ "$ASSUME_YES" = false ]; then
  if [ "$NETWORK" = "gnosis" ]; then
    echo "  MAINNET. Prerequisites (RUNBOOK + sprint B8): external audit closed, Safe 2/3 ready to receive the admin role,"
    echo "  definitive HMAC secrets set in production, recorder funded, alerting in place."
  fi
  read -r -p "  Type the network name ('${NETWORK}') to deploy: " CONFIRM
  [ "$CONFIRM" = "$NETWORK" ] || die "aborted"
fi

# ── 6. Deployment (script/DeployRegistry.s.sol re-checks chain, key, roles) ───────────────────────
step "6. Deployment"
BROADCAST_FLAG=()
[ "$DRY_RUN" = true ] || BROADCAST_FLAG=(--broadcast)
# ADMIN_PK is read by the Solidity script through vm.envUint: it is never put on the command line.
"$FORGE" script script/DeployRegistry.s.sol:DeployRegistry --rpc-url "$RPC_URL" "${BROADCAST_FLAG[@]}"

if [ "$DRY_RUN" = true ]; then
  ok "dry run done — rerun without --dry-run to deploy"
  exit 0
fi

# The broadcast file is the source of truth for the address, the tx hash and the block.
RUN_FILE="broadcast/DeployRegistry.s.sol/${CHAIN_ID}/run-latest.json"
[ -f "$RUN_FILE" ] || die "broadcast file not found: $RUN_FILE"
REGISTRY="$(jq -r '[.transactions[] | select(.transactionType == "CREATE" and .contractName == "CommonLinkRegistry")][0].contractAddress' "$RUN_FILE")"
DEPLOY_TX="$(jq -r '[.transactions[] | select(.transactionType == "CREATE" and .contractName == "CommonLinkRegistry")][0].hash' "$RUN_FILE")"
BLOCK_HEX="$(jq -r --arg h "$DEPLOY_TX" '[.receipts[] | select(.transactionHash == $h)][0].blockNumber' "$RUN_FILE")"
BLOCK="$("$CAST" to-dec "$BLOCK_HEX")"
[[ "$REGISTRY" =~ ^0x[0-9a-fA-F]{40}$ ]] || die "could not read the registry address from $RUN_FILE"
REGISTRY="$("$CAST" to-check-sum-address "$REGISTRY")"
ok "registry : $REGISTRY"
ok "tx       : $DEPLOY_TX (block $BLOCK)"

# ── 7. Post-deployment checks (read-only) ─────────────────────────────────────────────────────────
step "7. On-chain checks"
VERSION="$("$CAST" call "$REGISTRY" "VERSION()(string)" --rpc-url "$RPC_URL")"
RECORDER_ROLE="$("$CAST" keccak "RECORDER_ROLE")"
ADMIN_ROLE="0x0000000000000000000000000000000000000000000000000000000000000000"
[ "$("$CAST" call "$REGISTRY" "hasRole(bytes32,address)(bool)" "$ADMIN_ROLE" "$ADMIN_ADDR" --rpc-url "$RPC_URL")" = "true" ]       || die "admin role missing"
[ "$("$CAST" call "$REGISTRY" "hasRole(bytes32,address)(bool)" "$RECORDER_ROLE" "$RECORDER_ADDR" --rpc-url "$RPC_URL")" = "true" ] || die "recorder role missing"
[ "$("$CAST" call "$REGISTRY" "hasRole(bytes32,address)(bool)" "$ADMIN_ROLE" "$RECORDER_ADDR" --rpc-url "$RPC_URL")" = "false" ]   || die "recorder must not be admin"
ok "VERSION = $VERSION ; roles OK"

# ── 8. Source verification on the explorers (no key for Blockscout / Sourcify) ────────────────────
VERIFIED_BLOCKSCOUT=false; VERIFIED_SOURCIFY=false; VERIFIED_GNOSISSCAN="not-attempted"
if [ "$NETWORK" != "anvil" ] && [ "$SKIP_VERIFY" = false ]; then
  step "8. Source verification"
  ARGS="$("$CAST" abi-encode "constructor(address,address)" "$ADMIN_ADDR" "$RECORDER_ADDR")"
  CONTRACT="src/CommonLinkRegistry.sol:CommonLinkRegistry"

  # Blockscout: the instance's /api/ route, no API key.
  if "$FORGE" verify-contract "$REGISTRY" "$CONTRACT" --chain "$CHAIN_ID" \
       --verifier blockscout --verifier-url "${EXPLORER}/api/" --constructor-args "$ARGS" --watch; then
    VERIFIED_BLOCKSCOUT=true; ok "Blockscout verified"
  else
    echo "  ! Blockscout verification failed — retry later (RUNBOOK §3)"
  fi

  # Sourcify: no API key.
  if "$FORGE" verify-contract "$REGISTRY" "$CONTRACT" --chain "$CHAIN_ID" --verifier sourcify --watch; then
    VERIFIED_SOURCIFY=true; ok "Sourcify verified"
  else
    echo "  ! Sourcify verification failed — retry later (RUNBOOK §3)"
  fi

  # Gnosisscan (Etherscan V2 API): Gnosis mainnet only — Chiado coverage was not confirmed (RUNBOOK §3).
  if [ "$NETWORK" = "gnosis" ] && [ -n "${ETHERSCAN_API_KEY:-}" ]; then
    if "$FORGE" verify-contract "$REGISTRY" "$CONTRACT" --chain "$CHAIN_ID" \
         --verifier etherscan --etherscan-api-key "$ETHERSCAN_API_KEY" --constructor-args "$ARGS" --watch; then
      VERIFIED_GNOSISSCAN=true; ok "Gnosisscan verified"
    else
      VERIFIED_GNOSISSCAN=false; echo "  ! Gnosisscan verification failed — retry later"
    fi
  elif [ "$NETWORK" = "chiado" ]; then
    VERIFIED_GNOSISSCAN="not-supported-on-chiado-unconfirmed"
  fi
fi

# ── 9. Public deployment record (addresses only, no secret) ───────────────────────────────────────
step "9. deployments/${NETWORK}.json"
mkdir -p deployments
jq -n \
  --arg network "$NETWORK" --argjson chainId "$CHAIN_ID" --arg explorer "$EXPLORER" \
  --arg address "$REGISTRY" --arg tx "$DEPLOY_TX" --argjson block "$BLOCK" \
  --arg version "$VERSION" --arg deployedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg admin "$ADMIN_ADDR" --arg recorder "$RECORDER_ADDR" \
  --argjson blockscout "$VERIFIED_BLOCKSCOUT" --argjson sourcify "$VERIFIED_SOURCIFY" --arg gnosisscan "$VERIFIED_GNOSISSCAN" \
  '{
     network: $network, chainId: $chainId, explorer: $explorer,
     registry: {
       contract: "CommonLinkRegistry", version: ($version | gsub("\""; "")),
       address: $address, deployTxHash: $tx, blockNumber: $block, deployedAt: $deployedAt,
       constructorArgs: { admin: $admin, recorder: $recorder },
       verified: { blockscout: $blockscout, sourcify: $sourcify, gnosisscan: $gnosisscan }
     },
     badge: null,
     notes: "Public addresses only. Written by script/deploy-registry.sh. Never put a private key or a secret here."
   }' > "deployments/${NETWORK}.json"
ok "written (commit it: public data only)"

# ── 10. Next steps (human, RUNBOOK §1, §4, §5) ────────────────────────────────────────────────────
step "10. Next steps"
cat <<EOF
  1. Clever Cloud (${NETWORK}): ONCHAIN_REGISTRY_ADDRESS=${REGISTRY}  ONCHAIN_CHAIN_ID=${CHAIN_ID}
     + ONCHAIN_RPC_URL, ONCHAIN_RECORDER_PK (key of ${RECORDER_ADDR}), DONOR_ADDRESS_SECRET, ASSOCIATION_ADDRESS_SECRET;
     remove ONCHAIN_CURATOR_PK (obsolete).
  2. Redeploy the API (still mock), then: POST /api/admin/onchain/rederive-addresses (dry run, then ?dryRun=false).
  3. Set ONCHAIN_MOCK=false, redeploy, then POST /api/admin/onchain/backfill (dry run, then ?dryRun=false).
  4. Run the acceptance checklist of RUNBOOK §5 (explorer: ${EXPLORER:-n/a}/address/${REGISTRY}).
EOF
