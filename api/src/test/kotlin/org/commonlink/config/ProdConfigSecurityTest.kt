package org.commonlink.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import java.time.Duration

class ProdConfigSecurityTest {

    private val effectiveProps: Map<String, Any?> by lazy {
        val loader = YamlPropertySourceLoader()
        val merged = mutableMapOf<String, Any?>()

        fun load(name: String, path: String) {
            loader.load(name, ClassPathResource(path))
                .filterIsInstance<EnumerablePropertySource<*>>()
                .forEach { source ->
                    source.propertyNames.forEach { key -> merged[key] = source.getProperty(key) }
                }
        }

        load("base", "application.yml")
        load("prod", "application-prod.yml")
        merged
    }

    private fun prop(key: String): Any? = effectiveProps[key]

    @Test
    fun `vop demo-mode is false in prod`() {
        assertEquals(false, prop("app.vop.demo-mode"))
    }

    @Test
    fun `springdoc api-docs disabled in prod`() {
        assertEquals(false, prop("springdoc.api-docs.enabled"))
    }

    @Test
    fun `springdoc swagger-ui disabled in prod`() {
        assertEquals(false, prop("springdoc.swagger-ui.enabled"))
    }

    @Test
    fun `ddl-auto is validate in prod`() {
        assertEquals("validate", prop("spring.jpa.hibernate.ddl-auto"))
    }

    @Test
    fun `flyway is enabled in prod`() {
        assertEquals(true, prop("spring.flyway.enabled"))
    }

    @Test
    fun `show-sql is false in prod`() {
        assertEquals(false, prop("spring.jpa.show-sql"))
    }

    @Test
    fun `commonlink log level is not DEBUG in prod`() {
        assertNotEquals("DEBUG", prop("logging.level.org.commonlink"))
    }

    @Test
    fun `jwt secret has no insecure dev default`() {
        // TODO(security-sprint): base exposes insecure literal fallback via COMMON_LINK_SECRET; prod must supply secret via env only (no default)
        val secret = prop("app.jwt.secret") as? String ?: ""
        assertFalse(secret.contains("commonlink-dev-secret-key"))
    }

    @Disabled("onchain.mock intentionally stays true in prod until the blockchain component " +
        "is actually deployed — re-enable this test once it goes live.")
    @Test
    fun `onchain mock is disabled in prod`() {
        val mock = prop("onchain.mock")
        assertTrue(mock == null || mock == false,
            "onchain.mock must be absent or false in prod, was: $mock")
    }

    @Test
    fun `onchain association-address-secret may keep its dev placeholder while mock is true`() {
        // While onchain.mock stays true (see the disabled test above), the on-chain component
        // isn't wired up, so a dev placeholder here — like the sibling onchain.* keys
        // (donor-address-secret, recorder-pk, registry-address) — is accepted rather
        // than enforced via env-only injection the way app.jwt.secret / MOLLIE_API_KEY are.
        // La valeur attendue est celle de application-prod.yml, qui surcharge la base
        // (application.yml porte « change-in-staging »). Assertion sur la valeur exacte, comme
        // les autres contrôles de ce fichier : changer ce placeholder doit obliger à repasser ici.
        val secret = prop("onchain.association-address-secret") as? String ?: ""
        assertEquals("\${ASSOCIATION_ADDRESS_SECRET:dev-placeholder-change-in-prod}", secret)
    }

    @Test
    fun `mollie allow-fake-completion is false in prod`() {
        // C1: base defaults this true; prod must override it explicitly or the self-service
        // fake-KYC route ships active in production.
        assertEquals(false, prop("app.mollie.connect.allow-fake-completion"))
    }

    @Test
    fun `mollie connect mock is false in prod`() {
        assertEquals(false, prop("app.mollie.connect.mock"))
    }

    @Test
    fun `curator email is required with no empty default in prod`() {
        assertEquals("\${APP_CURATOR_EMAIL}", prop("app.curator.email"))
    }

    @Test
    fun `curator password is required with no empty default in prod`() {
        assertEquals("\${APP_CURATOR_PASSWORD}", prop("app.curator.password"))
    }

    @Test
    fun `compliance officer email is required with no empty default in prod`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_EMAIL}", prop("app.compliance-officer.email"))
    }

    @Test
    fun `compliance officer password is required with no empty default in prod`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_PASSWORD}", prop("app.compliance-officer.password"))
    }

    @Test
    fun `mollie test-mode is false in prod`() {
        // T1: base defaults test-mode true for local Connect sandbox; prod must pin false or live donations route to Mollie test mode
        assertEquals(false, prop("app.mollie.test-mode"))
    }

    @Test
    fun `mollie api-key does not inherit the committed test key in prod`() {
        // M1: base hardcodes a Mollie test key as the default; prod must require MOLLIE_API_KEY.
        val apiKey = prop("app.mollie.api-key") as? String ?: ""
        assertFalse(apiKey.contains("test_"), "prod must not inherit the base test key, was: $apiKey")
        assertEquals("\${MOLLIE_API_KEY}", apiKey)
    }

    @Test
    fun `mollie token-enc-key is required with no plaintext-inheriting default in prod`() {
        assertEquals("\${MOLLIE_TOKEN_ENC_KEY}", prop("app.mollie.token-enc-key"))
    }

    @Test
    fun `compliance encryption-key is required with no blank-fallback default in prod`() {
        assertEquals("\${COMPLIANCE_ENCRYPTION_KEY}", prop("commonlink.compliance.encryption-key"))
    }

    @Test
    fun `access-token-expiration is at most 1 hour in prod`() {
        val raw = prop("app.jwt.access-token-expiration") as? String ?: ""
        val duration = Duration.parse(raw)
        assertTrue(duration <= Duration.ofHours(1),
            "app.jwt.access-token-expiration must be ≤ PT1H in prod, was: $raw")
    }

    @Test
    fun `sanctions use-test-data is false in prod`() {
        // S1: test data mode routes ingestion to a bundled fixture bypassing the live DG Trésor registry.
        // Shipping it active in production would mean the register is never checked — a legal violation.
        assertEquals(false, prop("commonlink.sanctions.screening.use-test-data"))
    }

    @Test
    fun `actuator health details are not exposed to anonymous callers in prod`() {
        // /actuator/health is permitAll in SecurityConfig, so `always` hands the component detail
        // (database vendor and reachability, disk space, subsystem names) to any caller
        // (security audit 2026-08-20, M8).
        assertNotEquals("always", prop("management.endpoint.health.show-details"))
    }

    @Test
    fun `bridge demo-mode is env-overridable in prod`() {
        // Les identifiants Bridge de PRODUCTION ne sont pas encore provisionnés. Pinner
        // `demo-mode: false` ici rendait la prod non démarrable (client-id / client-secret /
        // api-version étaient trois `${...}` sans défaut). La prod démarre donc en mode démo et
        // bascule par variable d'environnement — même forme que app.security.trusted-proxy-count.
        // Condition de sortie : le jour où les identifiants existent, repasser le défaut de ce
        // fichier à false et réactiver le test désactivé ci-dessous — comme pour webhook-secret,
        // la garantie doit redevenir portée par le yml, pas par une variable d'environnement.
        assertEquals("\${BRIDGE_DEMO_MODE:true}", prop("app.bridge.demo-mode"))
    }

    @Disabled("app.bridge.demo-mode reste tolérant au mode démo tant que les identifiants Bridge " +
        "de PRODUCTION ne sont pas provisionnés sur Clever Cloud — ce jour-là, poser " +
        "`demo-mode: \${BRIDGE_DEMO_MODE:false}` dans application-prod.yml et réactiver ce test, " +
        "comme le test webhook-secret ci-dessous.")
    @Test
    fun `bridge demo-mode defaults to false in prod`() {
        assertEquals("\${BRIDGE_DEMO_MODE:false}", prop("app.bridge.demo-mode"))
    }

    @Test
    fun `bridge credentials tolerate a blank default in prod`() {
        // Corollaire du mode démo autorisé en prod : sans défaut, le placeholder non résolu fait
        // échouer le démarrage avant même le contrôle de BridgePaymentInitiationService, qui lui
        // n'exige les identifiants que lorsque demo-mode est false.
        assertEquals("\${BRIDGE_CLIENT_ID:}", prop("app.bridge.client-id"))
        assertEquals("\${BRIDGE_CLIENT_SECRET:}", prop("app.bridge.client-secret"))
        assertEquals("\${BRIDGE_API_VERSION:2025-01-15}", prop("app.bridge.api-version"))
    }

    @Test
    fun `bridge base-url placeholder is well-formed in prod`() {
        // Regression guard: this was previously `${BRIDGE_BASE_URLhttps://api.bridgeapi.io}` (no
        // `:` separator) — a startup-failing placeholder that this raw-string-comparison test
        // suite would otherwise never catch, since resolving it requires an actual app boot.
        assertEquals("\${BRIDGE_BASE_URL:https://api.bridgeapi.io}", prop("app.bridge.base-url"))
    }

    @Disabled("app.bridge.webhook-secret stays blank-tolerant until the webhook is created on " +
        "Bridge's PRODUCTION dashboard and BRIDGE_WEBHOOK_SECRET is set on Clever Cloud — " +
        "enable once that is done, mirroring the MOLLIE_API_KEY / COMPLIANCE_ENCRYPTION_KEY pattern.")
    @Test
    fun `bridge webhook-secret is required with no blank-fallback default in prod`() {
        assertEquals("\${BRIDGE_WEBHOOK_SECRET}", prop("app.bridge.webhook-secret"))
    }

    @Test
    fun `technical notification mailbox is required with no blank-fallback default in prod`() {
        // TechnicalAlertService sends nothing when this is blank — it logs a warning and returns.
        // The Bridge payout flow makes that unacceptable: PAYOUT_SETTLED_AFTER_RELEASE (a transfer
        // executed on funds already returned to the campaign) and PAYOUT_STUCK_IN_FLIGHT (a bank
        // that never reports execution) are deliberately *recorded and alerted* rather than
        // refused, and no other control catches either. A blank mailbox turns both into silence.
        assertEquals("\${APP_TECHNICAL_NOTIFICATION_EMAIL}", prop("app.technical.notification-email"))
    }

    @Test
    fun `technical alerting is not switched off by default in prod`() {
        // Corollary of the mailbox above: an operator can still disable alerting deliberately with
        // APP_TECHNICAL_ALERTS_ENABLED, but the shipped default must never be the off position.
        assertEquals("\${APP_TECHNICAL_ALERTS_ENABLED:true}", prop("app.technical.alerts-enabled"))
    }

    @Test
    fun `trusted-proxy-count is set in prod`() {
        // Rate limiting keys on the client address resolved by ClientIpResolver. Leaving the count
        // unset would fall back to the base-profile value of 0, i.e. every request behind the Clever
        // Cloud proxy sharing one bucket (security audit 2026-08-20, M2).
        assertEquals("\${APP_TRUSTED_PROXY_COUNT:1}", prop("app.security.trusted-proxy-count"))
    }

    // ───── Startup on-chain checks: RPC probe in real mode, WARN in mock mode ─────

    private fun onchainCfg(mock: Boolean = false) = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = OnchainRealModeValidator.ANVIL_CHAIN_ID,
        registryAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3",
        recorderPk = "0xabc",
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = "dev-placeholder-change-in-staging",
        associationAddressSecret = "dev-placeholder-change-in-staging",
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 1),
    )

    private val noSleep: (Long) -> Unit = {}

    @Test
    fun `real mode starts when the RPC answers on the configured chain with the registry deployed`() {
        assertDoesNotThrow { OnchainRealModeValidator.probeRpc(stubWeb3j(), onchainCfg(), 3, 2_000, noSleep) }
    }

    @Test
    fun `real mode refuses to start when the RPC stays unreachable, after 3 attempts 2 s apart`() {
        val pauses = mutableListOf<Long>()
        val ex = assertThrows(IllegalStateException::class.java) {
            OnchainRealModeValidator.probeRpc(stubWeb3j(unreachableCalls = 99), onchainCfg(), 3, 2_000) { pauses += it }
        }
        assertTrue(ex.message!!.contains("after 3 attempts"))
        assertTrue(ex.message!!.contains("onchain.rpc-url is unreachable"))
        // The connection error may carry the RPC URL and its token: never echoed.
        assertFalse(ex.message!!.contains("secret-token"))
        assertEquals(listOf(2_000L, 2_000L), pauses)
    }

    @Test
    fun `a brief RPC outage does not abort startup`() {
        assertDoesNotThrow {
            OnchainRealModeValidator.probeRpc(stubWeb3j(unreachableCalls = 2), onchainCfg(), 3, 2_000, noSleep)
        }
    }

    @Test
    fun `real mode refuses an RPC on another chain`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            OnchainRealModeValidator.probeRpc(stubWeb3j(chainIdHex = "0x27d8"), onchainCfg(), 3, 2_000, noSleep)
        }
        assertTrue(ex.message!!.contains("onchain.rpc-url is on chain 10200, onchain.chain-id is 31337"))
    }

    @Test
    fun `real mode refuses a chain without the registry contract`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            OnchainRealModeValidator.probeRpc(stubWeb3j(code = "0x"), onchainCfg(), 3, 2_000, noSleep)
        }
        assertTrue(ex.message!!.contains("no contract at onchain.registry-address"))
    }

    @Test
    fun `real mode startup runs the RPC probe`() {
        val web3j = stubWeb3j()
        OnchainRealModeValidator(onchainCfg(), providerOf(web3j)).validate()
        verify(exactly = 1) { web3j.ethChainId() }
        verify(exactly = 1) { web3j.ethGetCode("0x5FbDB2315678afecb367f032d93F642f64180aa3", any()) }
    }

    @Test
    fun `real mode without a Web3j client refuses to start`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            OnchainRealModeValidator(onchainCfg(), providerOf(null)).validate()
        }
        assertTrue(ex.message!!.contains("no Web3j client"))
    }

    private fun mockGuardWarnings(mock: Boolean, vararg profiles: String): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(OnchainMockGuard::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val env = mockk<Environment> { every { activeProfiles } returns arrayOf(*profiles) }
            OnchainMockGuard(env, onchainCfg(mock = mock)).validate()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.WARN }
    }

    @Test
    fun `mock mode logs a WARN at startup outside prod`() {
        val warnings = mockGuardWarnings(mock = true)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().formattedMessage.contains("on-chain writes are simulated"))
        assertTrue(warnings.single().formattedMessage.contains("profiles: default"))
    }

    @Test
    fun `mock mode logs its prod WARN under prod`() {
        val warnings = mockGuardWarnings(mock = true, "prod")
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().formattedMessage.contains("active profile 'prod'"))
    }

    @Test
    fun `real mode logs no mock WARN`() {
        assertTrue(mockGuardWarnings(mock = false, "staging").isEmpty())
    }
}
