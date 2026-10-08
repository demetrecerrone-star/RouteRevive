package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test

class CustomerBookingPageTest {
    @Test fun rejectsMissingBusinessContact() {
        assertFalse(CustomerBookingPage.ready(BusinessProfile(name = "My Company")))
        assertFalse(CustomerBookingPage.ready(BusinessProfile(
            name = "My Company", phone = "123")))
    }

    @Test fun generatedPageHasRequiredInputsAndManualSubmissionWarning() {
        val page = CustomerBookingPage.html(BusinessProfile(
            name = "North Shore Services", phone = "6125550102"))
        assertTrue(page.contains("North Shore Services"))
        assertTrue(page.contains("id=\"zip\""))
        assertTrue(page.contains("id=\"date\""))
        assertTrue(page.contains("id=\"service\""))
        assertTrue(page.contains("sms:"))
        assertTrue(page.contains("not confirmed until"))
        assertFalse(page.contains("fetch("))
    }

    @Test fun escapesInjectedHtmlFromBusinessName() {
        val page = CustomerBookingPage.html(BusinessProfile(
            name = "<script>alert(1)</script> Company",
            email = "owner@example.com"))
        assertFalse(page.contains("<script>alert(1)</script>"))
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(page.contains("mailto:"))
    }

    @Test fun invalidBusinessEmailDoesNotBecomeHtmlContact() {
        assertFalse(CustomerBookingPage.ready(BusinessProfile(
            name = "North", email = "bad'example.com")))
    }
}
