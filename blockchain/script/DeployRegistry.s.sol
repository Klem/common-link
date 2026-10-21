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
    }
}
