package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource
import java.time.Duration

/** Binding of [HelloAssoCashOutProperties] (`app.payments.helloasso`): defaults, overrides, and the three yml files. */
class HelloAssoCashOutPropertiesTest {

    @EnableConfigurationProperties(PaymentsProperties::class)
    private class Config

    @Test
    fun `a context without any value starts on the documented defaults`() {
        ApplicationContextRunner().withUserConfiguration(Config::class.java).run { context ->
            val props = context.getBean(PaymentsProperties::class.java).helloasso
            assertEquals(2, props.cashoutSettlementBusinessDays)
            assertEquals(5, props.cashoutInTransitAlertBusinessDays)
            assertEquals(300_000L, props.cashoutSyncInitialDelayMs)
            assertEquals(86_400_000L, props.cashoutSyncDelayMs)
            assertEquals(Duration.ofMinutes(15), props.cashoutRefreshMinInterval)
            assertEquals(Duration.ofSeconds(10), props.cashoutRefreshTimeout)
            assertEquals(Duration.ofDays(3), props.cashoutUnknownAlertAfter)
            assertEquals(3, props.cashoutSyncFailureAlertAfter)
            assertEquals(CashOutExportAmountUnit.EUROS, props.cashoutExportAmountUnit)
        }
    }

    @Test
    fun `the export amount unit binds from configuration`() {
        ApplicationContextRunner().withUserConfiguration(Config::class.java)
            .withPropertyValues("app.payments.helloasso.cashout-export-amount-unit=CENTS")
            .run { context ->
                assertEquals(CashOutExportAmountUnit.CENTS, context.getBean(PaymentsProperties::class.java).helloasso.cashoutExportAmountUnit)
            }
    }

    @Test
    fun `values are adjustable through configuration`() {
        ApplicationContextRunner().withUserConfiguration(Config::class.java)
            .withPropertyValues(
                "app.payments.helloasso.cashout-settlement-business-days=3",
                "app.payments.helloasso.cashout-refresh-min-interval=PT5M",
            )
            .run { context ->
                val props = context.getBean(PaymentsProperties::class.java).helloasso
                assertEquals(3, props.cashoutSettlementBusinessDays)
                assertEquals(Duration.ofMinutes(5), props.cashoutRefreshMinInterval)
            }
    }

    @Test
    fun `a negative settlement delay is refused`() {
        assertThrows<IllegalArgumentException> { HelloAssoCashOutProperties(cashoutSettlementBusinessDays = -1) }
    }

    @Test
    fun `the three yml files declare every key, with the same defaults`() {
        val keys = mapOf(
            "cashout-settlement-business-days" to "\${HELLOASSO_CASHOUT_SETTLEMENT_BUSINESS_DAYS:2}",
            "cashout-in-transit-alert-business-days" to "\${HELLOASSO_CASHOUT_IN_TRANSIT_ALERT_BUSINESS_DAYS:5}",
            "cashout-sync-initial-delay-ms" to "\${HELLOASSO_CASHOUT_SYNC_INITIAL_DELAY_MS:300000}",
            "cashout-sync-delay-ms" to "\${HELLOASSO_CASHOUT_SYNC_DELAY_MS:86400000}",
            "cashout-refresh-min-interval" to "\${HELLOASSO_CASHOUT_REFRESH_MIN_INTERVAL:PT15M}",
            "cashout-refresh-timeout" to "\${HELLOASSO_CASHOUT_REFRESH_TIMEOUT:PT10S}",
            "cashout-unknown-alert-after" to "\${HELLOASSO_CASHOUT_UNKNOWN_ALERT_AFTER:P3D}",
            "cashout-sync-failure-alert-after" to "\${HELLOASSO_CASHOUT_SYNC_FAILURE_ALERT_AFTER:3}",
            "cashout-export-amount-unit" to "\${HELLOASSO_CASHOUT_EXPORT_AMOUNT_UNIT:EUROS}",
        )
        for (file in listOf("application.yml", "application-staging.yml", "application-prod.yml")) {
            val source = YamlPropertySourceLoader().load(file, ClassPathResource(file)).first()
            keys.forEach { (key, value) ->
                assertEquals(value, source.getProperty("app.payments.helloasso.$key")?.toString(), "$file: $key")
            }
        }
    }
}
