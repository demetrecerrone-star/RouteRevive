package com.routerevive.app

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.LocalDate

/** Uses an isolated test installation and fictional records; never launches an SMS app. */
@RunWith(AndroidJUnit4::class)
class BusinessWorkflowSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today get() = LocalDate.now()

    private fun customer() = Customer(id = "smoke-customer", name = "Taylor Example", phone = "6125550142",
        zip = "56425", service = "House pressure washing", lastService = today.minusDays(220).toString(),
        consent = true, consentEvidence = "Fictional test permission", consentDate = today.minusDays(220).toString(), lastPrice = 150.0)
    private fun base() = WorkspaceSnapshot(customers = listOf(customer()),
        business = BusinessProfile(name = "Example Washing", workspaceId = "smoke-workspace"))

    private fun launch(state: WorkspaceSnapshot) {
        context.getSharedPreferences("routerevive_local_v1", Context.MODE_PRIVATE).edit().clear().commit()
        LocalStore(context).saveWorkspace(state)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())
    private fun tap(text: String) {
        val node = button(text)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }
    private fun navigate(page: String) {
        compose.onNodeWithTag("nav_$page").assertIsDisplayed().performClick()
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Connected tests uninstall the app afterward, including its external-files directory.
        // The dedicated device's Downloads folder survives until CI collects the evidence.
        shell("mkdir -p /sdcard/Download/routerevive-smoke")
        shell("screencap -p /sdcard/Download/routerevive-smoke/$name.png")
    }
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.readBytes() }
        }
    }

    @After fun close() {
        scenario?.close()
        scenario = null
        shell("settings put system font_scale 1.0")
    }

    @Test fun profitPlannerProtectsMarginAndSavesOnlyNearbyCustomers() {
        val anchor = customer().copy(id = "smoke-anchor", name = "Alex Example", phone = "6125550111",
            latitude = 46.35, longitude = -94.25)
        val nearby = customer().copy(latitude = 46.351, longitude = -94.25)
        val far = customer().copy(id = "smoke-far", name = "Morgan Example", phone = "6125550112",
            latitude = 47.0, longitude = -94.25)
        val appointment = Appointment(id = "smoke-anchor-booking", customerId = anchor.id,
            service = anchor.service, date = today.plusDays(1).toString(), time = "09:00", price = 150.0)
        launch(base().copy(customers = listOf(anchor, nearby, far), appointments = listOf(appointment)))
        tap("Plan a profitable opening")
        fun fill(label: String, value: String) {
            compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
        }
        fill("Whole crew cost per hour (USD)", "60")
        fill("Materials per job (USD)", "10")
        fill("Vehicle cost per job (USD)", "5")
        fill("Proposed discount %", "20")
        button("Create draft neighborhood offer").assertIsNotEnabled()
        compose.onNodeWithText("Below your margin target").assertExists()
        fill("Proposed discount %", "10")
        compose.onNodeWithText("3. Compare your offer").performScrollTo()
        screenshot("08_profit_comparison")
        shell("settings put system font_scale 1.5")
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Minimum price for 30% margin").performScrollTo().assertIsDisplayed()
        screenshot("09_profit_large_text")
        tap("Create draft neighborhood offer")
        val store = LocalStore(context)
        val saved = store.loadWorkspace()
        val campaign = saved.campaigns.single()
        assertEquals(listOf(nearby.id), campaign.recipients.map { it.customerId })
        assertEquals(1, campaign.capacity)
        assertEquals(75, campaign.profitPlan!!.blockMinutes)
        assertEquals(60.0, campaign.profitPlan!!.crewHourlyCost, 0.001)
        val backup = store.exportJson()
        BackupValidator.check(backup)
        store.importJson(backup)
        assertEquals(campaign, store.loadWorkspace().campaigns.single())
        screenshot("10_profit_draft_saved")
        tap("Review message")
        tap("Approve")
        tap("I sent this message")
        tap("Record sent")
        tap("Customer interested")
        tap("Confirm appointment")
        tap("Save appointment")
        val booked = store.loadWorkspace().appointments.single { it.customerId == nearby.id }
        assertEquals("10:00", booked.time)
        assertEquals(75, booked.durationMinutes)
        assertEquals(135.0, booked.price, 0.001)
    }

    @Test fun newCustomerUsesActualServiceAndPermissionDates() {
        launch(base().copy(customers = emptyList()))
        navigate("customers")
        tap("Add customer")
        fun fill(label: String, value: String) {
            compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
        }
        fill("Customer name", "Jordan Example")
        fill("Phone number", "6125550194")
        fill("5-digit ZIP code", "56425")
        fill("Previous job amount (USD)", "85.50")
        button("Save customer").assertIsNotEnabled()
        fill("Last completed service", today.minusDays(220).toString())
        compose.onNode(isToggleable()).performScrollTo().performClick()
        fill("Permission source and evidence", "Fictional documented permission")
        button("Save customer").assertIsNotEnabled()
        fill("Permission date", today.minusDays(220).toString())
        tap("Save customer")
        val saved = LocalStore(context).loadWorkspace()
        val added = saved.customers.single()
        assertEquals(today.minusDays(220).toString(), added.lastService)
        assertEquals(today.minusDays(220).toString(), added.consentDate)
        assertEquals(85.50, added.lastPrice, 0.001)
        assertTrue(EligibilityIndex(saved).reasons(added, added.zip, added.service).isEmpty())
        screenshot("00_customer_added")
    }

    @Test fun neighborhoodOfferCanBeReviewedApprovedAndBookedWithoutSendingAnything() {
        launch(base())
        screenshot("01_overview")
        tap("Build offer")
        screenshot("02_offer_builder")
        tap("Create offer for 1 customer")
        tap("Review message")
        tap("Approve")
        tap("I sent this message")
        tap("Record sent")
        tap("Customer interested")
        tap("Confirm appointment")
        tap("Save appointment")
        val saved = LocalStore(context).loadWorkspace()
        assertEquals(1, saved.campaigns.size)
        assertEquals("BOOKED", saved.campaigns.single().recipients.single().status)
        assertEquals(1, saved.appointments.size)
        assertEquals(saved.campaigns.single().id, saved.appointments.single().campaignId)
        assertEquals(today.toString(), saved.customers.single().lastContact)
        screenshot("03_campaign_booked")
        tap("Open job & invoice")
        compose.onNodeWithText("Taylor Example").assertIsDisplayed()
        // Android Back closes the job details before leaving the Jobs section.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        button("Open job").assertIsDisplayed()
        navigate("schedule")
        compose.onNodeWithText("Taylor Example").assertIsDisplayed()
    }

    @Test fun completedCampaignPaymentAppearsInJobsAndTheMainReportExactlyOnce() {
        val customer = customer()
        val campaign = Campaign(id = "smoke-offer", title = "Neighborhood today", businessName = "Example Washing",
            zip = customer.zip, service = customer.service, visitDate = today.toString(), expiryDate = today.toString(),
            price = 150.0, discountPct = 0, capacity = 4,
            recipients = listOf(Recipient(customer.id, status = "BOOKED", bookedAmount = 150.0)))
        val appointment = Appointment(id = "smoke-appointment", customerId = customer.id, service = customer.service,
            date = today.toString(), time = "08:00", price = 150.0, campaignId = campaign.id)
        launch(base().copy(campaigns = listOf(campaign), appointments = listOf(appointment)))
        tap("Campaigns")
        tap("Neighborhood today")
        tap("Complete service")
        tap("Confirm")
        tap("Record payment")
        tap("Save receipt")
        compose.onNodeWithText("Paid").assertIsDisplayed()
        val saved = LocalStore(context).loadWorkspace()
        assertEquals("COMPLETED", saved.appointments.single().status)
        assertEquals(today.toString(), saved.customers.single().lastService)
        assertEquals(1, saved.jobs.single().payments.size)
        assertEquals(150.0, BusinessLogic.financials(saved.jobs, saved.appointments).received, 0.001)
        assertEquals(150.0, CampaignFinance.received(saved, saved.campaigns.single()), 0.001)
        screenshot("04_campaign_paid")
        navigate("home")
        compose.onNodeWithText("$150.00").performScrollTo().assertIsDisplayed()
        screenshot("05_payment_report")
    }

    @Test fun searchNavigationLargeTextAndRotationKeepTheAppUsable() {
        launch(base())
        navigate("customers")
        compose.onNode(hasSetTextAction()).performClick().performTextInput("zz-no-match")
        compose.onNodeWithText("No customers found").assertIsDisplayed()
        navigate("more")
        compose.onNodeWithText("Customers & growth").assertIsDisplayed()
        shell("settings put system font_scale 1.5")
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Customers & growth").assertIsDisplayed()
        screenshot("06_large_text_tools")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithText("Business tools").assertIsDisplayed()
        screenshot("07_landscape_tools")
        navigate("customers")
        tap("Edit profile")
        compose.onNodeWithText("Taylor Example").assertIsDisplayed()
    }
}
