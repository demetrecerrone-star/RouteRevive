package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CampaignRulesTest {
    private val now = LocalDate.of(2026, 10, 7)
    private fun eligible() = Customer(
        name = "Test Customer", phone = "3125550188", zip = "60618",
        service = "House pressure washing", lastService = now.minusDays(220).toString(),
        consent = true, consentEvidence = "Signed SMS marketing consent on 2026-01-01"
    )
    private fun reasons(c: Customer, others: List<Customer> = listOf(c),
        campaigns: List<Campaign> = emptyList()) =
        CampaignRules.reasons(c, "60618", "House pressure washing", now, others, campaigns)

    @Test fun validRecordIsEligible() {
        assertTrue(reasons(eligible()).isEmpty())
    }
    @Test fun optOutBlocksContact() {
        assertTrue(reasons(eligible().copy(optedOut = true)).contains("Opted out"))
    }
    @Test fun missingEvidenceBlocksContact() {
        assertTrue(reasons(eligible().copy(consentEvidence = "")).any { it.contains("permission") })
    }
    @Test fun recentServiceBlocksCampaign() {
        assertTrue(reasons(eligible().copy(lastService = now.minusDays(10).toString()))
            .any { it.contains("not due") })
    }
    @Test fun cooldownBlocksRepeatedContact() {
        assertTrue(reasons(eligible().copy(lastContact = now.minusDays(3).toString()))
            .any { it.contains("60 days") })
    }
    @Test fun duplicatePhoneBlocksCampaign() {
        val a = eligible()
        val b = eligible().copy(phone = "(312) 555-0188")
        assertTrue(reasons(a, listOf(a,b)).any { it.contains("Duplicate") })
    }
    @Test fun otherApprovedCampaignBlocksDuplicateTargeting() {
        val a = eligible()
        val campaign = Campaign(
            title = "Test", businessName = "Business", zip = "60618",
            service = "House pressure washing", visitDate = now.plusDays(7).toString(),
            expiryDate = now.plusDays(6).toString(), price = 150.0,
            discountPct = 0, capacity = 3,
            recipients = listOf(Recipient(a.id, "APPROVED"))
        )
        assertTrue(reasons(a, listOf(a), listOf(campaign)).any { it.contains("active campaign") })
    }
    @Test fun messageContainsOptOut() {
        val a = eligible()
        val campaign = Campaign(
            title = "Test", businessName = "Example Washing", zip = "60618",
            service = "House pressure washing", visitDate = now.plusDays(7).toString(),
            expiryDate = now.plusDays(6).toString(), price = 150.0,
            discountPct = 10, capacity = 3
        )
        val msg = CampaignRules.message(a, campaign)
        assertTrue(msg.contains("STOP"))
        assertTrue(msg.contains("Example Washing"))
        assertTrue(msg.contains("10%"))
    }
}
