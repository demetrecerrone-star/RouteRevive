package com.routerevive.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RouteAppShell(
    page: String, title: String, openRequests: Int,
    onNavigate: (String) -> Unit, onBack: () -> Unit,
    onAdd: (() -> Unit)?,
    content: @Composable (PaddingValues) -> Unit
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun leaveField(action: () -> Unit) {
        focus.clearFocus()
        keyboard?.hide()
        action()
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.statusBarsPadding().fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!AppNavigation.isTopLevel(page)) {
                        IconButton(onClick = { leaveField(onBack) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back")
                        }
                    } else {
                        Image(painterResource(R.drawable.route_revive_icon), contentDescription = null,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ROUTEREVIVE", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                        Text(title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    if (onAdd != null) FilledTonalIconButton(onClick = { leaveField(onAdd) }) {
                        Icon(Icons.Default.Add,
                            if (page == "customers") "Add customer" else "Create campaign")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp) {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${section.page}"),
                        selected = AppNavigation.section(page) == section,
                        onClick = { leaveField { onNavigate(section.page) } },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            BadgedBox(badge = {
                                if (section == AppSection.MORE && openRequests > 0) {
                                    Badge(Modifier.semantics {
                                        contentDescription = "$openRequests open booking requests"
                                    }) { Text(if (openRequests > 99) "99+" else "$openRequests") }
                                }
                            }) {
                                Icon(when (section) {
                                    AppSection.OVERVIEW -> Icons.Default.Dashboard
                                    AppSection.CUSTOMERS -> Icons.Default.People
                                    AppSection.SCHEDULE -> Icons.Default.DateRange
                                    AppSection.JOBS -> Icons.Default.WorkOutline
                                    AppSection.MORE -> Icons.Default.GridView
                                }, contentDescription = null)
                            }
                        },
                        label = { Text(section.label, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        },
        content = content
    )
}
