package com.lumen.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.app.ui.dashboard.DashboardScreen
import com.lumen.app.ui.detail.AppDetailScreen
import com.lumen.app.ui.family.FamilyScreen
import com.lumen.app.ui.focus.FocusScreen
import com.lumen.app.ui.reports.ReportsScreen
import com.lumen.app.ui.settings.SettingsScreen
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.InkRaised
import com.lumen.app.ui.theme.TextMuted

private data class Tab(
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("Today", Icons.Rounded.Lightbulb),
    Tab("Reports", Icons.Rounded.Insights),
    Tab("Focus", Icons.Rounded.Timer),
    Tab("Watch", Icons.Rounded.Visibility),
    Tab("Settings", Icons.Rounded.Settings),
)

@Composable
fun LumenRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var appDetail by remember { mutableStateOf<Pair<String, String>?>(null) }

    val detail = appDetail
    if (detail != null) {
        BackHandler { appDetail = null }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                AppDetailScreen(
                    packageName = detail.first,
                    label = detail.second,
                    onBack = { appDetail = null },
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = InkRaised.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
            ) {
                tabs.forEachIndexed { index, tabItem ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(tabItem.icon, tabItem.label)
                        },
                        label = {
                            Text(tabItem.label, fontSize = 11.sp, maxLines = 1)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Amber,
                            selectedTextColor = Amber,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Amber.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                when (tab) {
                    0 -> DashboardScreen(
                        onOpenReports = { tab = 1 },
                        onAppClick = { pkg, label -> appDetail = pkg to label },
                    )
                    1 -> ReportsScreen(
                        onAppClick = { pkg, label -> appDetail = pkg to label },
                    )
                    2 -> FocusScreen()
                    3 -> FamilyScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
