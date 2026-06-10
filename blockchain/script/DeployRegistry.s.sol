// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Script, console} from "forge-std/Script.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";

contract DeployRegistry is Script {
    function run() external returns (CommonLinkRegistry registry) {
        address admin    = vm.envAddress("ADMIN_ADDR");
        address recorder = vm.envAddress("RECORDER_ADDR");
        address curator  = vm.envAddress("CURATOR_ADDR");

        // broadcast avec la clé admin : le déployeur EST l'admin
        vm.startBroadcast(vm.envUint("ADMIN_PK"));

        registry = new CommonLinkRegistry(admin, recorder, curator);

        vm.stopBroadcast();

        console.log("Registry deployed at:", address(registry));
        console.log("VERSION:", registry.VERSION());
        console.log("admin   :", admin);
        console.log("recorder:", recorder);
        console.log("curator :", curator);

        // ─── Vérification post-deploy (hors broadcast : lectures seules) ───
        bytes32 RECORDER_ROLE = registry.RECORDER_ROLE();
        bytes32 CURATOR_ROLE  = registry.CURATOR_ROLE();
        bytes32 ADMIN_ROLE    = registry.DEFAULT_ADMIN_ROLE();

        require(registry.hasRole(ADMIN_ROLE, admin),       "FAIL: admin role");
        require(registry.hasRole(RECORDER_ROLE, recorder), "FAIL: recorder role");
        require(registry.hasRole(CURATOR_ROLE, curator),   "FAIL: curator role");

        // Sanity : le recorder ne doit PAS etre admin (separation des pouvoirs)
        require(!registry.hasRole(ADMIN_ROLE, recorder), "FAIL: recorder is admin");

        console.log("All role checks passed");
    }
}