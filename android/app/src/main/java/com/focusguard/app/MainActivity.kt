package com.focusguard.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.focusguard.app.data.FocusGuardPrefs
import com.focusguard.app.service.BackgroundAndDnsManager
import com.focusguard.app.service.FocusGuardVpnService
import com.focusguard.app.ui.InterventionActivity
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = intent.getStringExtra("OPEN_TAB") ?: "home"

        setContent {
            FocusGuardApp(initialTab = initialTab)
        }
    }
}

@Composable
fun FocusGuardApp(initialTab: String = "home") {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialTab) }
    var showEmergencyUrge by remember { mutableStateOf(false) }

    var protectionActive by remember { mutableStateOf(FocusGuardPrefs.isProtectionActive(context)) }
    var dnsHost by remember { mutableStateOf(FocusGuardPrefs.getFamilyDnsHost(context)) }
    var urgesManaged by remember { mutableIntStateOf(FocusGuardPrefs.getUrgesManaged(context)) }
    var blockedCount by remember { mutableIntStateOf(FocusGuardPrefs.getBlockedCount(context)) }
    var batteryExempt by remember { mutableStateOf(BackgroundAndDnsManager.isIgnoringBatteryOptimizations(context)) }
    var systemPrivateDns by remember { mutableStateOf(BackgroundAndDnsManager.getActivePrivateDnsHostname(context)) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            FocusGuardPrefs.setProtectionActive(context, true)
            protectionActive = true
            BackgroundAndDnsManager.startVpnIfPermitted(context)
            BackgroundAndDnsManager.scheduleWatchdog(context)
            Toast.makeText(context, "Family DNS 24/7 Protection Activated", Toast.LENGTH_SHORT).show()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (protectionActive && VpnService.prepare(context) == null) {
            BackgroundAndDnsManager.startVpnIfPermitted(context)
        }
    }

    val isHeartfulness = selectedTab == "heartfulness"
    val bgColor = if (isHeartfulness) Color(0xFFFBFBF9) else Color(0xFF090D16)

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            NavigationBar(
                containerColor = if (isHeartfulness) Color(0xFFF3F5EE) else Color(0xFF0B1120)
            ) {
                val navItems = listOf(
                    Triple("home", "Home", Icons.Default.Home),
                    Triple("protection", "Shield", Icons.Default.Shield),
                    Triple("heartfulness", "Heart", Icons.Default.Favorite),
                    Triple("goals", "Goals", Icons.Default.Star),
                    Triple("progress", "Progress", Icons.Default.Assessment)
                )
                navItems.forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == id,
                        onClick = { selectedTab = id },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (id == "heartfulness") Color(0xFF065F46) else Color(0xFF22D3EE),
                            selectedTextColor = if (id == "heartfulness") Color(0xFF065F46) else Color(0xFF22D3EE),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                "home" -> HomeScreenNative(
                    protectionActive = protectionActive,
                    dnsHost = dnsHost,
                    urgesManaged = urgesManaged,
                    blockedCount = blockedCount,
                    onEmergencyUrge = { showEmergencyUrge = true },
                    onStartVpn = {
                        val prep = VpnService.prepare(context)
                        if (prep != null) vpnLauncher.launch(prep)
                        else {
                            BackgroundAndDnsManager.startVpnIfPermitted(context)
                            protectionActive = true
                        }
                    },
                    onAttemptDisable = {
                        context.startActivity(
                            Intent(context, InterventionActivity::class.java).apply {
                                putExtra("EXTRA_TRIGGER_REASON", "Disabling 24/7 Protection")
                            }
                        )
                    },
                    onOpenProtection = { selectedTab = "protection" }
                )

                "protection" -> ProtectionScreenNative(
                    dnsHost = dnsHost,
                    batteryExempt = batteryExempt,
                    systemPrivateDns = systemPrivateDns,
                    onSelectFamilyDns = { host, ip ->
                        FocusGuardPrefs.setFamilyDns(context, host, ip)
                        dnsHost = host
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Family DNS Host", host))
                        Toast.makeText(context, "Copied $host — paste in Private DNS", Toast.LENGTH_LONG).show()
                        if (VpnService.prepare(context) == null) {
                            BackgroundAndDnsManager.startVpnIfPermitted(context)
                        }
                    },
                    onRequestVpn = {
                        val prep = VpnService.prepare(context)
                        if (prep != null) vpnLauncher.launch(prep)
                        else BackgroundAndDnsManager.startVpnIfPermitted(context)
                    },
                    onOpenPrivateDnsSettings = {
                        BackgroundAndDnsManager.openSystemPrivateDnsSettings(context)
                    },
                    onOpenAlwaysOnVpn = {
                        BackgroundAndDnsManager.openAlwaysOnVpnSettings(context)
                    },
                    onRequestBatteryExemption = {
                        BackgroundAndDnsManager.requestBatteryOptimizationExemption(context)
                        batteryExempt = BackgroundAndDnsManager.isIgnoringBatteryOptimizations(context)
                    },
                    onOpenAccessibility = {
                        BackgroundAndDnsManager.openAccessibilitySettings(context)
                    },
                    onRefreshStatus = {
                        batteryExempt = BackgroundAndDnsManager.isIgnoringBatteryOptimizations(context)
                        systemPrivateDns = BackgroundAndDnsManager.getActivePrivateDnsHostname(context)
                    }
                )

                "heartfulness" -> HeartfulnessScreenNative()

                "goals" -> GoalsScreenNative()

                "progress" -> ProgressScreenNative(
                    streakDays = FocusGuardPrefs.getStreakDays(context),
                    urgesManaged = urgesManaged,
                    blockedCount = blockedCount
                )
            }

            if (showEmergencyUrge) {
                EmergencyUrgeDialog(
                    onDismiss = { showEmergencyUrge = false },
                    onVictory = {
                        urgesManaged = FocusGuardPrefs.incrementUrgesManaged(context)
                        showEmergencyUrge = false
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreenNative(
    protectionActive: Boolean,
    dnsHost: String,
    urgesManaged: Int,
    blockedCount: Int,
    onEmergencyUrge: () -> Unit,
    onStartVpn: () -> Unit,
    onAttemptDisable: () -> Unit,
    onOpenProtection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("FOCUS GUARD", color = Color(0xFF22D3EE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "\"Your future is bigger than your temporary urges.\"",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        // Emergency Button
        Button(
            onClick = onEmergencyUrge,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("I AM HAVING AN URGE", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
        }

        // Protection Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (protectionActive) "24/7 FAMILY DNS PROTECTION ACTIVE" else "PROTECTION PAUSED",
                color = if (protectionActive) Color(0xFF34D399) else Color(0xFFF87171),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Active Family DNS: $dnsHost", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartVpn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2))
                ) {
                    Text("Run 24/7 VPN", fontSize = 12.sp, color = Color.White)
                }
                OutlinedButton(
                    onClick = onAttemptDisable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Turn Off", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
            TextButton(onClick = onOpenProtection) {
                Text("Configure Family DNS & Battery Exemption →", color = Color(0xFF22D3EE), fontSize = 12.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "Urges Conquered", urgesManaged.toString(), Color(0xFF34D399))
            StatCard(Modifier.weight(1f), "Blocked Threats", blockedCount.toString(), Color(0xFFA78BFA))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, accent: Color) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ProtectionScreenNative(
    dnsHost: String,
    batteryExempt: Boolean,
    systemPrivateDns: String?,
    onSelectFamilyDns: (String, String) -> Unit,
    onRequestVpn: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    onOpenAlwaysOnVpn: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Family DNS & 24/7 Background", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Permit system Private DNS, local VPN, and unrestricted battery so adult blocking runs 24/7.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        // Family DNS Presets
        Button(
            onClick = { onSelectFamilyDns("family.adguard-dns.com", "94.140.14.15") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Use & Copy AdGuard Family (family.adguard-dns.com)", fontSize = 12.sp, color = Color.White)
        }

        Button(
            onClick = { onSelectFamilyDns("adult-filter-dns.cleanbrowsing.org", "185.228.168.168") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Use CleanBrowsing Adult Filter (185.228.168.168)", fontSize = 12.sp, color = Color.White)
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        Text("Required 24/7 Android Permissions", color = Color(0xFF22D3EE), fontSize = 13.sp, fontWeight = FontWeight.Bold)

        PermissionActionCard(
            title = "1. Local Family DNS VPN (Active: $dnsHost)",
            subtitle = "Blocks adult domains over Wi-Fi and 4G/5G continuously.",
            buttonLabel = "Permit VPN",
            onClick = onRequestVpn
        )
        PermissionActionCard(
            title = "2. Android Private DNS (${systemPrivateDns ?: "Check Settings"})",
            subtitle = "Network & internet -> Private DNS -> Paste family.adguard-dns.com.",
            buttonLabel = "Open Settings",
            onClick = onOpenPrivateDnsSettings
        )
        PermissionActionCard(
            title = "3. Unrestricted Battery (${if (batteryExempt) "Granted" else "Action Needed"})",
            subtitle = "Stops Android Doze from killing Focus Guard in background.",
            buttonLabel = if (batteryExempt) "Exempted" else "Allow 24/7",
            onClick = onRequestBatteryExemption
        )
        PermissionActionCard(
            title = "4. Always-On VPN Lock",
            subtitle = "Enable Always-on VPN for Focus Guard in System VPN Settings.",
            buttonLabel = "VPN Settings",
            onClick = onOpenAlwaysOnVpn
        )
        PermissionActionCard(
            title = "5. Accessibility Browser Guard",
            subtitle = "Detects restricted URLs in Chrome & incognito tabs.",
            buttonLabel = "Accessibility",
            onClick = onOpenAccessibility
        )

        OutlinedButton(onClick = onRefreshStatus, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh Permission Status", color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun PermissionActionCard(title: String, subtitle: String, buttonLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(buttonLabel, fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
fun HeartfulnessScreenNative() {
    var meditationSeconds by remember { mutableIntStateOf(20 * 60) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && meditationSeconds > 0) {
            delay(1000L)
            meditationSeconds--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("HEARTFULNESS SANCTUARY", color = Color(0xFF065F46), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Connect with Inner Stillness", color = Color(0xFF1C1917), fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text(
            "Sit comfortably, close your eyes gently, and suppose the Divine Light is already present within your heart.",
            color = Color(0xFF57534E),
            fontSize = 14.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFE7E5E4), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%02d:%02d", meditationSeconds / 60, meditationSeconds % 60),
                color = Color(0xFF1C1917),
                fontSize = 44.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { running = !running },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (running) "Pause Meditation" else "Begin Meditation", color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3F6EE), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("Universal Prayer (9:00 PM)", color = Color(0xFF065F46), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "\"O Master! Thou art the real goal of human life. We are yet but slaves of wishes putting bar to our advancement. Thou art the only God and Power to bring us up to that stage.\"",
                color = Color(0xFF292524),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun GoalsScreenNative() {
    val context = LocalContext.current
    var lifeGoal by remember { mutableStateOf(FocusGuardPrefs.getLifeGoal(context)) }
    var careerGoal by remember { mutableStateOf(FocusGuardPrefs.getCareerGoal(context)) }
    var family by remember { mutableStateOf(FocusGuardPrefs.getFamilyExpectations(context)) }
    var message by remember { mutableStateOf(FocusGuardPrefs.getMotivationalMessage(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Purpose & Family Goals", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(value = lifeGoal, onValueChange = { lifeGoal = it }, label = { Text("My Biggest Life Goal") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = careerGoal, onValueChange = { careerGoal = it }, label = { Text("Career Goal") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = family, onValueChange = { family = it }, label = { Text("Family Expectations") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Personal Motivational Message") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                FocusGuardPrefs.saveGoals(context, lifeGoal, careerGoal, family, message)
                Toast.makeText(context, "Goals Saved Offline", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2))
        ) {
            Text("Save Personal Commitments", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProgressScreenNative(streakDays: Int, urgesManaged: Int, blockedCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Discipline & Progress", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        StatCard(Modifier.fillMaxWidth(), "Porn-Free Days", "$streakDays Days", Color(0xFFFBBF24))
        StatCard(Modifier.fillMaxWidth(), "Urges Conquered", "$urgesManaged", Color(0xFF34D399))
        StatCard(Modifier.fillMaxWidth(), "Adult Content Blocked", "$blockedCount", Color(0xFF22D3EE))
    }
}

@Composable
fun EmergencyUrgeDialog(onDismiss: () -> Unit, onVictory: () -> Unit) {
    var seconds by remember { mutableIntStateOf(600) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000L)
            seconds--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Put Your Phone Away & Breathe", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Take a slow 4-7-8 breath. Place your phone facedown for 10 minutes, drink cold water, or take a brisk walk.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
                Text(
                    text = String.format("%02d:%02d", seconds / 60, seconds % 60),
                    color = Color(0xFF22D3EE),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onVictory,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Urge Reduced (Log Victory)", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}
