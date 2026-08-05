package com.lumen.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.app.ui.OnboardingViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.GlowRing
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val container = LocalContainer.current
    val vm: OnboardingViewModel = viewModel(factory = viewModelFactoryFor(container))
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    var usageGranted by remember { mutableStateOf(vm.hasUsageAccess()) }
    var notifGranted by remember { mutableStateOf(vm.hasNotificationAccess()) }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        usageGranted = vm.hasUsageAccess()
        notifGranted = vm.hasNotificationAccess()
        onPauseOrDispose { }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1226), Ink)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> IntroPage(onNext = { scope.launch { pagerState.animateScrollToPage(1) } })
                    1 -> PermissionPage(
                        icon = Icons.Rounded.PhoneAndroid,
                        title = "Read your screen time",
                        body = "Lumen looks at how long apps stay open — never at what's on them. " +
                            "It needs one switch in your system settings. Everything stays on your phone.",
                        granted = usageGranted,
                        grantedLabel = "Access granted",
                        buttonLabel = "Open phone settings",
                        onPrimary = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onSecondary = { scope.launch { pagerState.animateScrollToPage(2) } },
                        secondaryLabel = "I've already allowed it",
                    )
                    2 -> PermissionPage(
                        icon = Icons.Rounded.Notifications,
                        title = "Count your notifications",
                        body = "Optional: Lumen simply counts how many arrive — it never reads them.",
                        steps = listOf(
                            "Tap \"Open app settings\" below",
                            "On it, tap ⋮ → \"Allow restricted settings\"",
                            "Tap \"Open notification access\" and switch Lumen on",
                        ),
                        granted = notifGranted,
                        grantedLabel = "Notifications enabled",
                        buttonLabel = "Open app settings",
                        onPrimary = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                        middleSlot = {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(52.dp),
                            ) {
                                Text("Open notification access")
                            }
                        },
                        onSecondary = { scope.launch { pagerState.animateScrollToPage(3) } },
                        secondaryLabel = "Skip for now",
                    )
                    else -> DonePage(vm)
                }
            }
            Spacer(Modifier.height(40.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 22.dp else 8.dp)
                            .background(
                                color = if (i == pagerState.currentPage) Amber else TextMuted.copy(alpha = 0.5f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun PageBody(body: String) {
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun IntroPage(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlowRing(progress = 0.42f, size = 200.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    null,
                    tint = Amber,
                    modifier = Modifier.size(46.dp),
                )
                Text(
                    "Lumen",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        PageTitle("See your day\nin a new light")
        PageBody(
            "Lumen watches how you use your phone — screen time, pickups, " +
                "notifications — and turns it into a quiet, beautiful report. " +
                "Private by design: everything stays on your device.",
        )
        Spacer(Modifier.height(30.dp))
        PrimaryButton("Get started") { onNext() }
    }
}

@Composable
private fun PermissionPage(
    icon: ImageVector,
    title: String,
    body: String,
    steps: List<String>? = null,
    granted: Boolean,
    grantedLabel: String,
    buttonLabel: String,
    onPrimary: () -> Unit,
    middleSlot: (@Composable () -> Unit)? = null,
    onSecondary: () -> Unit,
    secondaryLabel: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(116.dp)
                .background(
                    Brush.radialGradient(listOf(Mint.copy(alpha = 0.14f), Color.Transparent)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (granted) Mint else Amber,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        PageTitle(title)
        PageBody(body)
        Spacer(Modifier.height(18.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = Mint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    grantedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Mint,
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Continue") { onSecondary() }
        } else {
            if (steps != null) {
                StepList(steps)
                Spacer(Modifier.height(14.dp))
            }
            PrimaryButton(buttonLabel) { onPrimary() }
            if (middleSlot != null) {
                Spacer(Modifier.height(10.dp))
                middleSlot()
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSecondary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp),
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
private fun StepList(steps: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(Amber.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DonePage(vm: OnboardingViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlowRing(progress = 1f, size = 180.dp) {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = Amber,
                modifier = Modifier.size(56.dp),
            )
        }
        PageTitle("Your report arrives tonight")
        PageBody(
            "Lumen is now quietly watching. Open it any time to see how " +
                "your day is going — and tomorrow morning, your first full report.",
        )
        Spacer(Modifier.height(30.dp))
        PrimaryButton("Start with Lumen") { vm.complete() }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Ink,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(52.dp),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
