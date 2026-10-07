package org.commonlink.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssociationProfileTest {

    private val user = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)

    private fun association(
        status: AssociationStatus = AssociationStatus.ACTIVE,
        widgetToken: String? = "clk_x",
        destinationStatus: CampaignStatus? = CampaignStatus.LIVE,
    ): AssociationProfile {
        val assoc = AssociationProfile(user = user, name = "Asso", identifier = "775671356", status = status)
        assoc.widgetToken = widgetToken
        if (destinationStatus != null) {
            assoc.widgetDestinationCampaign = Campaign(association = assoc, name = "Camp", status = destinationStatus)
        }
        return assoc
    }

    @Test
    fun `eligible when active, widget token set, and destination campaign LIVE`() {
        assertThat(association().hasEligibleWidget()).isTrue()
    }

    @Test
    fun `not eligible when the association is SUSPENDED`() {
        assertThat(association(status = AssociationStatus.SUSPENDED).hasEligibleWidget()).isFalse()
    }

    @Test
    fun `not eligible when no widget token is set`() {
        assertThat(association(widgetToken = null).hasEligibleWidget()).isFalse()
    }

    @Test
    fun `not eligible when there is no destination campaign`() {
        assertThat(association(destinationStatus = null).hasEligibleWidget()).isFalse()
    }

    @Test
    fun `not eligible when the destination campaign is not LIVE`() {
        assertThat(association(destinationStatus = CampaignStatus.DRAFT).hasEligibleWidget()).isFalse()
    }
}
