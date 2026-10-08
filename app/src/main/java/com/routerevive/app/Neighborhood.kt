package com.routerevive.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.*

/** Distances are straight-line estimates, NOT driving distances or ETA. */
object NeighborhoodLogic {
    fun validPin(customer: Customer): Boolean =
        customer.latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
        customer.longitude?.let { it.isFinite() && it in -180.0..180.0 } == true

    fun distanceMiles(a: Customer, b: Customer): Double {
        if (!validPin(a) || !validPin(b)) return Double.POSITIVE_INFINITY
        val lat1 = Math.toRadians(a.latitude!!)
        val lat2 = Math.toRadians(b.latitude!!)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude!! - a.longitude!!)
        val h = sin(dLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return 3958.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun nearbyCandidates(anchor: Customer, customers: List<Customer>, campaigns: List<Campaign>,
                         appointments: List<Appointment>, radiusMiles: Double,
                         today: LocalDate): List<Pair<Customer, Double>> {
        if (!validPin(anchor)) return emptyList()
        return customers.asSequence()
            .filter { it.id != anchor.id && validPin(it) }
            .filter { candidate ->
                val booked = appointments.any { a -> a.customerId == candidate.id &&
                    a.status in setOf("SCHEDULED", "IN_PROGRESS") &&
                    runCatching { !LocalDate.parse(a.date).isBefore(today) }.getOrDefault(false) }
                val tested = candidate.copy(futureBooked = candidate.futureBooked || booked)
                CampaignRules.reasons(tested, anchor.zip, anchor.service, today, customers, campaigns).isEmpty()
            }
            .map { it to distanceMiles(anchor, it) }
            .filter { it.second <= radiusMiles }
            .sortedBy { it.second }
            .toList()
    }

    fun scheduledRoute(day: String, appointments: List<Appointment>,
                       customers: List<Customer>): List<Pair<Appointment, Customer>> =
        appointments.asSequence()
            .filter { it.date == day && it.status in setOf("SCHEDULED", "IN_PROGRESS") }
            .sortedBy { it.time }
            .mapNotNull { a ->
                customers.firstOrNull { it.id == a.customerId && validPin(it) }?.let { a to it }
            }
            .toList()

    fun drivingDirections(stops: List<Customer>): Uri? {
        if (stops.size < 2) return null
        val chosen = stops.take(8)
        fun coordinate(customer: Customer) = "${customer.latitude},${customer.longitude}"
        return Uri.Builder().scheme("https").authority("www.google.com")
            .appendPath("maps").appendPath("dir")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("origin", coordinate(chosen.first()))
            .appendQueryParameter("destination", coordinate(chosen.last()))
            .apply {
                if (chosen.size > 2) appendQueryParameter("waypoints",
                    chosen.subList(1, chosen.lastIndex).joinToString("|") { coordinate(it) })
            }
            .appendQueryParameter("travelmode", "driving")
            .build()
    }

    fun stopDirections(customer: Customer): Uri? {
        if (!validPin(customer)) return null
        return Uri.Builder().scheme("https").authority("www.google.com")
            .appendPath("maps").appendPath("dir")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", "${customer.latitude},${customer.longitude}")
            .appendQueryParameter("travelmode", "driving")
            .build()
    }
}

private fun viewScale(context: Context): Float = context.resources.displayMetrics.density

private data class PinProposal(val customerId: String, val latitude: Double,
                               val longitude: Double, val address: String)

/** Explicit, one-at-a-time geocoding. Never background-geocode the entire customer database. */
@Composable
fun NeighborhoodMapScreen(
    customers: List<Customer>,
    campaigns: List<Campaign>,
    appointments: List<Appointment>,
    onSavePin: (String, Double, Double) -> Unit,
    onStartCampaign: (String, String) -> Unit,
    onAppointmentStatus: (Appointment, String) -> Unit,
    onOpenSchedule: () -> Unit
) {
    val context = LocalContext.current
    val pins = customers.filter(NeighborhoodLogic::validPin)
    var anchorId by remember { mutableStateOf("") }
    var radiusMiles by remember { mutableFloatStateOf(5f) }
    var routeDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var locatingId by remember { mutableStateOf<String?>(null) }
    var pinProposal by remember { mutableStateOf<PinProposal?>(null) }
    var error by remember { mutableStateOf("") }
    var showDirectionsConfirm by remember { mutableStateOf(false) }
    var showDistancePreview by remember { mutableStateOf(false) }
    var navigateToCustomer by remember { mutableStateOf<Customer?>(null) }
    var anchorDropdown by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val anchor = customers.firstOrNull { it.id == anchorId && NeighborhoodLogic.validPin(it) }
        ?: pins.firstOrNull()
    val candidates = anchor?.let {
        NeighborhoodLogic.nearbyCandidates(it, customers, campaigns, appointments,
            radiusMiles.toDouble(), LocalDate.now())
    } ?: emptyList()
    val route = NeighborhoodLogic.scheduledRoute(routeDate, appointments, customers)
    val suggested = RoutePlannerLogic.distanceFirstPreview(route)
    val routeToDraw = if (showDistancePreview) suggested else route
    val unmappedScheduled = appointments.count { a ->
        a.date == routeDate && a.status in setOf("SCHEDULED", "IN_PROGRESS") &&
            customers.none { it.id == a.customerId && NeighborhoodLogic.validPin(it) }
    }
    val missing = customers.filter { !NeighborhoodLogic.validPin(it) && it.address.isNotBlank() }

    // A map must never be nested in a vertical scrolling container:
    // native drags and pinches need to own their gestures in *every* direction.
    var showMap by remember { mutableStateOf(true) }

    // Repaint overlays only after actual pin/route changes, not whenever slider,
    // text field or nearby-customer data recomposes. This avoids map flicker and
    // native info-window/marker jumps during a gesture.
    LaunchedEffect(mapView, pins, routeToDraw, appointments, routeDate) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.clear()
        pins.forEach { customer ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(customer.latitude!!, customer.longitude!!)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val markerStatus = RoutePlannerLogic.markerStatus(customer.id, routeDate, appointments)
                title = "${customer.name} · ${markerStatus.replace('_', ' ')}"
                snippet = customer.service
                val markerColor = when (markerStatus) {
                    "IN_PROGRESS" -> android.graphics.Color.rgb(251, 191, 36)
                    "SCHEDULED" -> android.graphics.Color.rgb(96, 165, 250)
                    "COMPLETED" -> android.graphics.Color.rgb(74, 222, 128)
                    "CANCELLED" -> android.graphics.Color.rgb(148, 163, 184)
                    else -> android.graphics.Color.rgb(192, 132, 252)
                }
                val pinSize = (viewScale(map.context) * 24).toInt()
                setIcon(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(markerColor)
                    setStroke((viewScale(map.context) * 3).toInt().coerceAtLeast(2),
                        android.graphics.Color.WHITE)
                    setSize(pinSize, pinSize)
                })
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { marker, _ ->
                    marker.showInfoWindow()
                    true
                }
            })
        }
        val points = routeToDraw.map { (_, customer) ->
            GeoPoint(customer.latitude!!, customer.longitude!!)
        }
        if (points.size >= 2) {
            map.overlays.add(Polyline(map).apply {
                setPoints(points)
                outlinePaint.color = android.graphics.Color.rgb(74, 222, 128)
                outlinePaint.strokeWidth = 5f
            })
        }
        map.invalidate()
    }

    // Move to a selected customer only when the user explicitly selects it.
    // Native panning must never trigger recentering, even on recomposition.
    LaunchedEffect(anchorId, mapView) {
        if (anchorId.isNotBlank()) {
            val selected = pins.firstOrNull { it.id == anchorId }
            if (selected != null) {
                mapView?.controller?.animateTo(GeoPoint(selected.latitude!!, selected.longitude!!))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NEIGHBORHOOD INTELLIGENCE", color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = showMap, onClick = { showMap = true },
                label = { Text("Explore map") })
            FilterChip(selected = !showMap, onClick = {
                showMap = false
                mapView = null
            }, label = { Text("Opportunities & routes") })
        }
        if (showMap) {
            Card(Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${pins.size} mapped customers", fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = pins.isNotEmpty(),
                                onClick = { mapView?.controller?.zoomOut() }) {
                                Text("−", fontSize = 22.sp)
                            }
                            TextButton(enabled = pins.isNotEmpty(),
                                onClick = { mapView?.controller?.zoomIn() }) {
                                Text("+", fontSize = 22.sp)
                            }
                            TextButton(enabled = anchor != null, onClick = {
                                anchor?.let {
                                    mapView?.controller?.animateTo(GeoPoint(it.latitude!!, it.longitude!!))
                                }
                            }) { Text("Center", fontSize = 12.sp) }
                        }
                    }
                    if (pins.isNotEmpty()) {
                        // Dedicated, bounded map surface. Neither map tiles nor
                        // marker popups can grow the surrounding layout.
                        Box(Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(12.dp))) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    Configuration.getInstance().userAgentValue = viewContext.packageName
                                    MapView(viewContext).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        setBuiltInZoomControls(false)
                                        isClickable = true
                                        controller.setZoom(12.5)
                                        val initial = anchor ?: pins.first()
                                        controller.setCenter(GeoPoint(initial.latitude!!, initial.longitude!!))
                                        // Native MapView receives all horizontal/vertical
                                        // panning and multi-pointer pinch events.
                                        setOnTouchListener { _, event ->
                                            when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN,
                                                MotionEvent.ACTION_POINTER_DOWN ->
                                                    parent?.requestDisallowInterceptTouchEvent(true)
                                                MotionEvent.ACTION_UP,
                                                MotionEvent.ACTION_CANCEL ->
                                                    parent?.requestDisallowInterceptTouchEvent(false)
                                            }
                                            false
                                        }
                                        mapView = this
                                    }
                                },
                                // Never reset overlays or camera while dragging.
                                update = {}
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center) {
                            Text("No pins yet. Open Opportunities & routes to locate an address, or enter coordinates in a customer profile.",
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text("Drag in any direction · Pinch to zoom · Tap a pin for details",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("© OpenStreetMap contributors · Internet required",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        } else {
            // All lower-page fields scroll independently of the map view.
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NEARBY REPEAT-SERVICE OPPORTUNITIES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (pins.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { anchorDropdown = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Anchor: ${anchor?.name ?: "Select customer"}", maxLines = 1)
                }
                DropdownMenu(expanded = anchorDropdown, onDismissRequest = { anchorDropdown = false }) {
                    pins.forEach { customer ->
                        DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                            anchorId = customer.id
                            anchorDropdown = false
                        })
                    }
                }
            }
            Text("Search radius: ${radiusMiles.toInt()} miles (straight-line)", fontSize = 13.sp)
            Slider(value = radiusMiles, onValueChange = { radiusMiles = it }, valueRange = 1f..25f,
                steps = 23)
            Text("${candidates.size} eligible customers near the anchor (same service and ZIP)",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            candidates.take(12).forEach { (customer, distance) ->
                Text("• ${customer.name} — ${"%.1f".format(Locale.US, distance)} mi · ${customer.service}",
                    fontSize = 13.sp)
            }
            if (candidates.isNotEmpty() && anchor != null) {
                Button(onClick = { onStartCampaign(anchor.zip, anchor.service) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Create matching neighborhood campaign")
                }
            } else {
                Text("Only contacts with documented marketing permission and no campaign exclusions appear here.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        HorizontalDivider()
        Text("SMART DAILY ROUTE PLANNER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        OutlinedTextField(value = routeDate, onValueChange = { routeDate = it },
            label = { Text("Service date (YYYY-MM-DD)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        if (runCatching { LocalDate.parse(routeDate) }.isFailure) {
            Text("Enter a valid date.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } else {
            if (route.isEmpty()) {
                Text("No active mapped appointments on this date.", fontSize = 13.sp)
            } else {
                val chronologicalMiles = RoutePlannerLogic.straightLineMiles(route)
                val suggestedMiles = RoutePlannerLogic.straightLineMiles(suggested)
                Text("Booked order: ${"%.1f".format(Locale.US, chronologicalMiles)} miles between pins (straight line)",
                    fontSize = 12.sp)
                if (route.size >= 3) {
                    Text("Distance-first preview: ${"%.1f".format(Locale.US, suggestedMiles)} miles between pins",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showDistancePreview, onCheckedChange = { showDistancePreview = it })
                        Text("Preview distance-first order on map", fontSize = 12.sp)
                    }
                    Text("Distance-first ordering is a suggestion ONLY. It can conflict with booked times; appointments are never automatically rearranged. Reschedule first before driving a different order.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                val displayRoute = if (showDistancePreview) suggested else route
                displayRoute.forEachIndexed { index, (appointment, customer) ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${index + 1}. ${appointment.time} · ${customer.name}",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${appointment.service} · ${appointment.status.replace('_', ' ')}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { navigateToCustomer = customer }) { Text("Navigate") }
                                if (appointment.status == "SCHEDULED") {
                                    TextButton(onClick = { onAppointmentStatus(appointment, "IN_PROGRESS") }) {
                                        Text("Start")
                                    }
                                }
                                if (appointment.status in setOf("SCHEDULED", "IN_PROGRESS")) {
                                    TextButton(onClick = { onAppointmentStatus(appointment, "COMPLETED") }) {
                                        Text("Complete")
                                    }
                                }
                            }
                        }
                    }
                }
                if (route.size >= 2) Button(onClick = { showDirectionsConfirm = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Directions in BOOKED order (${min(route.size, 8)} stops)")
                }
                if (route.size > 8) Text("Directions include only the first eight booked stops.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                RoutePlannerLogic.nextStop(route)?.let { (_, customer) ->
                    OutlinedButton(onClick = { navigateToCustomer = customer },
                        modifier = Modifier.fillMaxWidth()) { Text("Navigate to next booked job: ${customer.name}") }
                }
            }
            if (unmappedScheduled > 0) Text("$unmappedScheduled active appointment(s) have no saved map pin. Add their locations before relying on this route.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
                Text("Open schedule to reschedule or cancel jobs")
            }
        }
        Text("Blue: scheduled · Yellow: in progress · Green: completed · Gray: cancelled · Purple: customer",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        Text("Distances and drawn map lines are straight-line estimates, NOT driving distances or arrival times. Check traffic, appointment windows, and directions in your navigation app.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)

        HorizontalDivider()
        Text("ADD CUSTOMER MAP PINS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (missing.isEmpty()) Text("All customers with street addresses have pins. You can adjust coordinates in their customer profiles.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        missing.take(30).forEach { customer ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${customer.address}, ${customer.zip}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                }
                TextButton(enabled = locatingId == null, onClick = {
                    locatingId = customer.id
                    error = ""
                    val query = "${customer.address}, ${customer.zip}, USA"
                    thread(name = "route-revive-address-search") {
                        val result = runCatching {
                            if (!Geocoder.isPresent()) error("Address search unavailable on this device")
                            @Suppress("DEPRECATION")
                            val found = Geocoder(context.applicationContext, Locale.US)
                                .getFromLocationName(query, 1)?.firstOrNull()
                                ?: error("Address not found; enter coordinates manually")
                            require(found.latitude in -90.0..90.0 && found.longitude in -180.0..180.0)
                            PinProposal(customer.id, found.latitude, found.longitude,
                                found.getAddressLine(0) ?: query)
                        }
                        Handler(Looper.getMainLooper()).post {
                            locatingId = null
                            result.onSuccess { pinProposal = it }
                                .onFailure { error = it.message ?: "Address lookup unavailable" }
                        }
                    }
                }) { Text(if (locatingId == customer.id) "Finding…" else "Locate") }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Text("Privacy: Map tiles reveal the viewed map area to OpenStreetMap infrastructure. Locating an address may send it to your device's geocoding provider. Opening directions shares chosen coordinates with Google Maps. No bulk address lookup or background location tracking.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))

            }
        }
    }

    if (pinProposal != null) {
        val proposal = pinProposal!!
        AlertDialog(onDismissRequest = { pinProposal = null }, title = { Text("Save customer pin?") },
            text = {
                Text("Address found: ${proposal.address}\n\nCoordinates: " +
                    "${"%.5f".format(Locale.US, proposal.latitude)}, " +
                    "${"%.5f".format(Locale.US, proposal.longitude)}\n\nConfirm the location is correct before saving.")
            },
            confirmButton = { TextButton(onClick = {
                onSavePin(proposal.customerId, proposal.latitude, proposal.longitude)
                anchorId = proposal.customerId
                pinProposal = null
            }) { Text("Save pin") } },
            dismissButton = { TextButton(onClick = { pinProposal = null }) { Text("Cancel") } })
    }
    navigateToCustomer?.let { customer ->
        AlertDialog(onDismissRequest = { navigateToCustomer = null },
            title = { Text("Open navigation?") },
            text = { Text("Share the saved map location for ${customer.name} with your maps app? Check the address before driving.") },
            confirmButton = { TextButton(onClick = {
                navigateToCustomer = null
                NeighborhoodLogic.stopDirections(customer)?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                        .onFailure { error = "No app can open navigation." }
                }
            }) { Text("Open navigation") } },
            dismissButton = { TextButton(onClick = { navigateToCustomer = null }) { Text("Cancel") } })
    }

    if (showDirectionsConfirm) {
        AlertDialog(onDismissRequest = { showDirectionsConfirm = false },
            title = { Text("Share route with Google Maps?") },
            text = { Text("Booked-order stop coordinates will open in Google Maps. No phone numbers or notes are shared. Check traffic, travel times and all appointment windows before driving.") },
            confirmButton = { TextButton(onClick = {
                showDirectionsConfirm = false
                NeighborhoodLogic.drivingDirections(route.map { it.second })?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                        .onFailure { error = "No app can open driving directions." }
                }
            }) { Text("Open directions") } },
            dismissButton = { TextButton(onClick = { showDirectionsConfirm = false }) { Text("Cancel") } })
    }
}
