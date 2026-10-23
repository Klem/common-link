// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Script, console} from "forge-std/Script.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";

/**
 * @notice Deploys the CommonLinkRegistry v2 (roles: admin + recorder only).
 *
 *         Normally launched by `script/deploy-registry.sh`, which wraps the full procedure of
 *         `blockchain/RUNBOOK.md` (pre-checks, build/tests, broadcast, post-checks, explorer verification,
 *         deployments/<network>.json).
 *
 *         Deployment order of the on-chain stack:
 *           1. this script (registry);
 *           2. the DonorBadge contract, with this registry as its single minter (sprint B5);
 *           3. the admin calls `registry.setBadgeContract(badge)` (write-once).
 *
 *         Env (operator workstation only, never on a server):
 *           ADMIN_ADDR    — receives DEFAULT_ADMIN_ROLE (pause, setBadgeContract). Mainnet: handed to a Safe (B8).
 *           RECORDER_ADDR — receives RECORDER_ROLE (the backend hot wallet, single write role).
 *           ADMIN_PK      — private key of ADMIN_ADDR; signs the deployment. Never printed.
 *
 *         Optional, Anvil only: SEED_CAMPAIGN_ID (+ SEED_ASSOCIATION_ADDR, SEED_SIREN_HASH, SEED_BUDGET_HASH,
 *         RECORDER_PK) registers one local association and campaign after the deployment — see `_seedLocal`.
 *
 *         Guards: supported chains only (Anvil 31337, Gnosis Chiado 10200, Gnosis 100); the key must match
 *         ADMIN_ADDR; the recorder must be non-zero and distinct from the admin; roles are checked after
 *         deployment.
 */
contract DeployRegistry is Script {
    uint256 internal constant ANVIL = 31337;
    uint256 internal constant GNOSIS_CHIADO = 10200;
    uint256 internal constant GNOSIS = 100;

    function run() external returns (CommonLinkRegistry registry) {
        require(
            block.chainid == ANVIL || block.chainid == GNOSIS_CHIADO || block.chainid == GNOSIS,
            "Unsupported chain: expected Anvil (31337), Gnosis Chiado (10200) or Gnosis (100)"
        );

        address admin    = vm.envAddress("ADMIN_ADDR");
        address recorder = vm.envAddress("RECORDER_ADDR");
        uint256 adminPk  = vm.envUint("ADMIN_PK");

        require(admin != address(0), "ADMIN_ADDR must not be zero");
        require(recorder != address(0), "RECORDER_ADDR must not be zero");
        require(recorder != admin, "RECORDER_ADDR must differ from ADMIN_ADDR");
        // The deployer IS the admin: refuse a key that does not belong to ADMIN_ADDR.
        require(vm.addr(adminPk) == admin, "ADMIN_PK does not match ADMIN_ADDR");

        vm.startBroadcast(adminPk);
        registry = new CommonLinkRegistry(admin, recorder);
        vm.stopBroadcast();

        console.log("Registry deployed at:", address(registry));
        console.log("VERSION:", registry.VERSION());
        console.log("chainId :", block.chainid);
        console.log("admin   :", admin);
        console.log("recorder:", recorder);

        // Post-deploy verification (read-only, outside the broadcast).
        bytes32 recorderRole = registry.RECORDER_ROLE();
        bytes32 adminRole    = registry.DEFAULT_ADMIN_ROLE();
        require(registry.hasRole(adminRole, admin),       "FAIL: admin role");
        require(registry.hasRole(recorderRole, recorder), "FAIL: recorder role");
        // Separation of powers: the hot wallet must never be admin.
        require(!registry.hasRole(adminRole, recorder),   "FAIL: recorder is admin");
        require(address(registry.badgeContract()) == address(0), "FAIL: badge contract must start unset");

        console.log("All role checks passed");

        // Optional local seed (Anvil only): set by deploy-registry.sh --seed-campaign from the local database.
        bytes32 seedCampaignId = vm.envOr("SEED_CAMPAIGN_ID", bytes32(0));
        if (seedCampaignId != bytes32(0)) {
            _seedLocal(registry, recorder, seedCampaignId);
        }
    }

    /**
     * @notice Registers one association and one of its campaigns right after a LOCAL deployment, as the backend
     *         would have done: the local database already holds data registered nowhere on this fresh node.
     *         Anvil only — never a backfill on a public chain (decision 26). The backend worker then finds these
     *         registrations on-chain and marks its own registration jobs DONE without a transaction.
     *
     *         Env (all computed by deploy-registry.sh from the local database, never typed by hand):
     *           SEED_ASSOCIATION_ADDR — the association address derived by the API (HMAC of its id)
     *           SEED_SIREN_HASH       — keccak256 of its SIREN (or RNA identifier)
     *           SEED_BUDGET_HASH      — the campaign's current budget hash
     *           RECORDER_PK           — private key of RECORDER_ADDR (Anvil public test key); signs the two writes
     * @param registry       the registry just deployed
     * @param recorder       RECORDER_ADDR, the only role allowed to register
     * @param seedCampaignId campaign UUID encoded as bytes32 (16 UUID bytes + 16 zero bytes)
     */
    function _seedLocal(CommonLinkRegistry registry, address recorder, bytes32 seedCampaignId) internal {
        require(block.chainid == ANVIL, "SEED_* is local only: refused outside Anvil (31337)");

        address association = vm.envAddress("SEED_ASSOCIATION_ADDR");
        bytes32 sirenHash   = vm.envBytes32("SEED_SIREN_HASH");
        bytes32 budgetHash  = vm.envBytes32("SEED_BUDGET_HASH");
        uint256 recorderPk  = vm.envUint("RECORDER_PK");
        require(vm.addr(recorderPk) == recorder, "RECORDER_PK does not match RECORDER_ADDR");

        vm.startBroadcast(recorderPk);
        registry.registerAssociation(association, sirenHash);
        registry.registerCampaign(seedCampaignId, association, budgetHash);
        vm.stopBroadcast();

        // Read back what the backend worker will compare its jobs with.
        CommonLinkRegistry.Association memory a = registry.getAssociation(association);
        CommonLinkRegistry.Campaign memory c = registry.getCampaign(seedCampaignId);
        require(a.sirenHash == sirenHash && a.registeredAt != 0, "FAIL: seeded association");
        require(c.association == association && c.budgetHash == budgetHash, "FAIL: seeded campaign");

        console.log("Seeded association:", association);
        console.log("Seeded campaign   :");
        console.logBytes32(seedCampaignId);
    }
}
