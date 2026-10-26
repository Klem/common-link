// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Script, console} from "forge-std/Script.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {ICommonLinkRegistryReader} from "../src/interfaces/ICommonLinkRegistryReader.sol";

/**
 * @notice Deploys the CommonLinkRegistry hub (roles: admin with a two-step delayed transfer, recorder, pauser).
 *
 *         Normally launched by `script/deploy-registry.sh`, which wraps the full procedure of
 *         `blockchain/RUNBOOK.md` (pre-checks, build/tests, broadcast, post-checks, explorer verification,
 *         deployments/<network>.json).
 *
 *         The hub never calls another contract: satellites (e.g. the soulbound badge) are deployed afterwards with
 *         this registry's address and read it through ICommonLinkRegistryReader. Nothing to wire on the hub.
 *
 *         Env (operator workstation only, never on a server):
 *           ADMIN_ADDR    — receives DEFAULT_ADMIN_ROLE (resume, role management). Mainnet: handed to a Safe (B8)
 *                           through the two-step transfer.
 *           ADMIN_DELAY   — delay (seconds) of that transfer: 0 on Anvil only; 3600 on Chiado, 259200 on Gnosis
 *                           (decision 40, set by deploy-registry.sh).
 *           RECORDER_ADDR — receives RECORDER_ROLE (the backend hot wallet, single write role).
 *           PAUSER_ADDR   — receives PAUSER_ROLE (dedicated backend key, pause only).
 *           ADMIN_PK      — private key of ADMIN_ADDR; signs the deployment. Never printed.
 *
 *         Optional, Anvil only: SEED_CAMPAIGN_ID (+ SEED_ASSOCIATION_ADDR, SEED_SIREN_HASH, SEED_BUDGET_HASH,
 *         RECORDER_PK) registers one local association and campaign after the deployment — see `_seedLocal`.
 *
 *         Guards: supported chains only (Anvil 31337, Gnosis Chiado 10200, Gnosis 100); the key must match
 *         ADMIN_ADDR; admin, recorder and pauser must be non-zero and pairwise distinct; a zero admin delay is
 *         refused outside Anvil; roles, admin, delay and the ERC-165 reader interface are checked after deployment.
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

        address admin      = vm.envAddress("ADMIN_ADDR");
        address recorder   = vm.envAddress("RECORDER_ADDR");
        address pauser     = vm.envAddress("PAUSER_ADDR");
        uint48  adminDelay = uint48(vm.envUint("ADMIN_DELAY"));
        uint256 adminPk    = vm.envUint("ADMIN_PK");

        require(admin != address(0), "ADMIN_ADDR must not be zero");
        require(recorder != address(0), "RECORDER_ADDR must not be zero");
        require(pauser != address(0), "PAUSER_ADDR must not be zero");
        require(recorder != admin, "RECORDER_ADDR must differ from ADMIN_ADDR");
        require(pauser != admin, "PAUSER_ADDR must differ from ADMIN_ADDR");
        require(pauser != recorder, "PAUSER_ADDR must differ from RECORDER_ADDR (dedicated key, decision 31)");
        require(adminDelay != 0 || block.chainid == ANVIL, "ADMIN_DELAY must be non-zero outside Anvil");
        // The deployer IS the admin: refuse a key that does not belong to ADMIN_ADDR.
        require(vm.addr(adminPk) == admin, "ADMIN_PK does not match ADMIN_ADDR");

        vm.startBroadcast(adminPk);
        registry = new CommonLinkRegistry(adminDelay, admin, recorder, pauser);
        vm.stopBroadcast();

        console.log("Registry deployed at:", address(registry));
        console.log("VERSION:", registry.VERSION());
        console.log("chainId :", block.chainid);
        console.log("admin   :", admin);
        console.log("delay(s):", uint256(adminDelay));
        console.log("recorder:", recorder);
        console.log("pauser  :", pauser);

        // Post-deploy verification (read-only, outside the broadcast).
        bytes32 recorderRole = registry.RECORDER_ROLE();
        bytes32 pauserRole   = registry.PAUSER_ROLE();
        bytes32 adminRole    = registry.DEFAULT_ADMIN_ROLE();
        require(registry.defaultAdmin() == admin,         "FAIL: default admin");
        require(registry.defaultAdminDelay() == adminDelay, "FAIL: admin delay");
        require(registry.hasRole(recorderRole, recorder), "FAIL: recorder role");
        require(registry.hasRole(pauserRole, pauser),     "FAIL: pauser role");
        // Separation of powers: hot keys are never admin, and each holds a single role.
        require(!registry.hasRole(adminRole, recorder),   "FAIL: recorder is admin");
        require(!registry.hasRole(adminRole, pauser),     "FAIL: pauser is admin");
        require(!registry.hasRole(pauserRole, recorder),  "FAIL: recorder is pauser");
        require(
            registry.supportsInterface(type(ICommonLinkRegistryReader).interfaceId),
            "FAIL: reader interface not advertised"
        );
        require(address(registry).balance == 0, "FAIL: registry holds funds");

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
