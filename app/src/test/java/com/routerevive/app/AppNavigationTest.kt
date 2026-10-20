package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    @Test fun customerDetailsKeepCustomersSelectedAndReturnToTheList() {
        listOf("new_customer", "edit_customer", "customer_history").forEach { page ->
            assertEquals(AppSection.CUSTOMERS, AppNavigation.section(page))
            assertEquals("customers", AppNavigation.backTarget(page))
            assertFalse(AppNavigation.isTopLevel(page))
        }
    }

    @Test fun nestedCampaignBackNeverSkipsTheCampaignList() {
        assertEquals("campaigns", AppNavigation.backTarget("campaign_detail"))
        assertEquals("campaigns", AppNavigation.backTarget("new_campaign"))
        assertEquals("more", AppNavigation.backTarget("campaigns"))
        assertEquals(AppSection.MORE, AppNavigation.section("campaign_detail"))
    }

    @Test fun backFromEveryOtherPrimaryTabReturnsToOverview() {
        AppSection.entries.filterNot { it == AppSection.OVERVIEW }.forEach { section ->
            assertTrue(AppNavigation.isTopLevel(section.page))
            assertEquals("home", AppNavigation.backTarget(section.page))
            assertEquals(section, AppNavigation.section(section.page))
        }
        assertNull(AppNavigation.backTarget("home"))
    }
}
