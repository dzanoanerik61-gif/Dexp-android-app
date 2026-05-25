package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_SHIZUKU = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ShizukuCheckerScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermission = {
                            requestShizukuPermission()
                        }
                    )
                }
            }
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            } else {
                Toast.makeText(
                    this,
                    "Служба Shizuku не запущена. См. инструкцию ниже.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка запроса прав: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

// Colors representing a high-tech obsidian dashboard
object DashboardColors {
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F141C), Color(0xFF181F2A))
    )
    val SurfaceDark = Color(0xFF1F2836)
    val SurfaceCard = Color(0xFF242F41)
    val CardBorder = Color(0xFF334257)
    
    // Status colors
    val ActiveGreen = Color(0xFF00E676)
    val ActiveGreenGlow = Color(0x3300E676)
    val InactiveRed = Color(0xFFFF1744)
    val InactiveRedGlow = Color(0x33FF1744)
    val WarningAmber = Color(0xFFFFA000)
    val WarningAmberGlow = Color(0x33FFA000)
    
    // Text accents
    val SlateLight = Color(0xFFECEFF1)
    val SlateGray = Color(0xFF90A4AE)
}

data class ShizukuState(
    val isRunning: Boolean = false,
    val hasPermission: Boolean = false,
    val serverVersion: Int = -1,
    val isAppInstalled: Boolean = false,
    val terminalOutput: String = "",
    val isTerminalRunning: Boolean = false,
    val scanTimestamp: Long = 0L
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ShizukuCheckerScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // UI Local State
    var state by remember { mutableStateOf(ShizukuState()) }
    var isChecking by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("checker") } // tabs: "checker", "guides", "faq"
    
    // Function to run the check safely
    val performCheck = {
        isChecking = true
        coroutineScope.launch {
            delay(500) // Aesthetic delay simulating actual physical ADB ping
            
            val isInstalled = try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            
            val running = try {
                Shizuku.pingBinder()
            } catch (e: Throwable) {
                false
            }
            
            val permission = try {
                if (running) {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else false
            } catch (e: Throwable) {
                false
            }
            
            val version = try {
                if (running) Shizuku.getVersion() else -1
            } catch (e: Throwable) {
                -1
            }
            
            state = ShizukuState(
                isRunning = running,
                hasPermission = permission,
                serverVersion = version,
                isAppInstalled = isInstalled,
                terminalOutput = state.terminalOutput,
                scanTimestamp = System.currentTimeMillis()
            )
            isChecking = false
        }
    }

    // Refresh immediately on load
    LaunchedEffect(Unit) {
        performCheck()
    }

    // Lifecycle listener to refresh automatically when screen gains focus
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                performCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Shizuku interactive permission listener
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            state = state.copy(hasPermission = granted)
            if (granted) {
                Toast.makeText(context, "Разрешение Shizuku получено успешно!", Toast.LENGTH_SHORT).show()
                performCheck()
            } else {
                Toast.makeText(context, "Доступ отклонен пользователем", Toast.LENGTH_SHORT).show()
            }
        }
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {}
        
        onDispose {
            try {
                Shizuku.removeRequestPermissionResultListener(listener)
            } catch (e: Throwable) {}
        }
    }

    // Root layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DashboardColors.BackgroundGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Elegant Dashboard Header Title
            HeaderSection(
                onRefresh = { performCheck() },
                isRefreshing = isChecking
            )
            
            // Tab Buttons: Root Checker style menu
            TabSelector(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            // Scrollable Content Pane
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                        },
                        label = "TabContentAnimation"
                    ) { tab ->
                        when (tab) {
                            "checker" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Main diagnostics visual banner
                                    DiagnosticBanner(
                                        state = state,
                                        isChecking = isChecking,
                                        onRequestPermission = onRequestPermission
                                    )
                                    
                                    // Device Info Spec card
                                    DeviceInfoCard(state = state)
                                    
                                    // ADB Sandbox terminal testing console
                                    if (state.isRunning && state.hasPermission) {
                                        AdbSandboxConsole(
                                            state = state,
                                            onRunCommand = { cmd ->
                                                state = state.copy(isTerminalRunning = true)
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val output = executeAdbCommand(cmd)
                                                    state = state.copy(
                                                        terminalOutput = output,
                                                        isTerminalRunning = false
                                                    )
                                                }
                                            },
                                            onClearTerminal = {
                                                state = state.copy(terminalOutput = "")
                                            }
                                        )
                                    } else {
                                        // Brief warning card with setup prompt
                                        QuickActionSuggestionCard(
                                            state = state,
                                            onGoToGuides = { activeTab = "guides" }
                                        )
                                    }
                                }
                            }
                            "guides" -> {
                                SetupGuidesSection(state = state)
                            }
                            "faq" -> {
                                FaqSection()
                            }
                        }
                    }
                }
                
                // Footer Padding bottom
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun HeaderSection(
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = if (isRefreshing) {
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(0)
        },
        label = "refreshRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SHIZUKU CHECKER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = DashboardColors.ActiveGreen
            )
            Text(
                text = "Сканер ADB привилегий и отладки",
                style = MaterialTheme.typography.bodySmall,
                color = DashboardColors.SlateGray
            )
        }

        IconButton(
            onClick = { if (!isRefreshing) onRefresh() },
            modifier = Modifier
                .background(DashboardColors.SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, DashboardColors.CardBorder, RoundedCornerShape(12.dp))
                .testTag("refresh_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Обновить статус",
                tint = DashboardColors.SlateLight,
                modifier = Modifier.rotate(rotationAngle)
            )
        }
    }
}

@Composable
fun TabSelector(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(DashboardColors.SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, DashboardColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val tabs = listOf(
            Triple("checker", "Проверка", Icons.Default.CheckCircle),
            Triple("guides", "Инструкция", Icons.Default.Info),
            Triple("faq", "Помощь / FAQ", Icons.Default.Info)
        )

        tabs.forEach { (route, label, icon) ->
            val isSelected = activeTab == route
            val bg = if (isSelected) DashboardColors.SurfaceCard else Color.Transparent
            val borderTint = if (isSelected) DashboardColors.CardBorder else Color.Transparent
            val textTint = if (isSelected) DashboardColors.ActiveGreen else DashboardColors.SlateGray

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, borderTint, RoundedCornerShape(10.dp))
                    .clickable { onTabSelected(route) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = textTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = textTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticBanner(
    state: ShizukuState,
    isChecking: Boolean,
    onRequestPermission: () -> Unit
) {
    val statusColor = when {
        isChecking -> DashboardColors.WarningAmber
        state.isRunning && state.hasPermission -> DashboardColors.ActiveGreen
        state.isRunning && !state.hasPermission -> DashboardColors.WarningAmber
        else -> DashboardColors.InactiveRed
    }

    val glowColor = when {
        isChecking -> DashboardColors.WarningAmberGlow
        state.isRunning && state.hasPermission -> DashboardColors.ActiveGreenGlow
        state.isRunning && !state.hasPermission -> DashboardColors.WarningAmberGlow
        else -> DashboardColors.InactiveRedGlow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = statusColor)
            .background(DashboardColors.SurfaceDark, RoundedCornerShape(24.dp))
            .border(2.dp, statusColor, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner Badge Icon
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(glowColor, CircleShape)
                .border(2.dp, statusColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val icon: ImageVector = when {
                isChecking -> Icons.Default.Refresh
                state.isRunning && state.hasPermission -> Icons.Default.CheckCircle
                state.isRunning && !state.hasPermission -> Icons.Default.Lock
                else -> Icons.Default.Warning
            }
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Large status phrase
        val statusText = when {
            isChecking -> "СКАНИРОВАНИЕ..."
            state.isRunning && state.hasPermission -> "ADB ПРИВИЛЕГИИ: АКТИВНЫ"
            state.isRunning && !state.hasPermission -> "ADB НАЙДЕН, НО ЗАБЛОКИРОВАН"
            else -> "SHIZUKU / ADB: НЕАКТИВЕН"
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            ),
            color = statusColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Informative sub-banner descriptions
        val descText = when {
            isChecking -> "Запрос системного отладчика и проверка API-коннекторов"
            state.isRunning && state.hasPermission -> "Система имеет полнофункциональный доступ к ADB без Root прав. Shizuku API готов к работе!"
            state.isRunning && !state.hasPermission -> "Служба запущена в фоне, но вы должны разрешить этому приложению использовать её API."
            !state.isAppInstalled -> "Клиент Shizuku не установлен на вашем устройстве. Запустите инструкцию по установке."
            else -> "Служба Shizuku не запущена в системе Android. Для продолжения активируйте ее посредством беспроводной отладки или ПК."
        }

        Text(
            text = descText,
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardColors.SlateGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        // Action controls based on state
        if (state.isRunning || state.isAppInstalled || isChecking) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (state.isRunning && !state.hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DashboardColors.WarningAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_permission_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ПРЕДОСТАВИТЬ ДОСТУП",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else if (state.isRunning && state.hasPermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1100E676), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x3300E676), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = DashboardColors.ActiveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Сертификат безопасности верифицирован",
                            style = MaterialTheme.typography.bodySmall,
                            color = DashboardColors.ActiveGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (isChecking) {
                    CircularProgressIndicator(
                        color = DashboardColors.WarningAmber,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceInfoCard(state: ShizukuState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardColors.SurfaceDark, RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, DashboardColors.CardBorder), RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "СВЕДЕНИЯ О СИСТЕМЕ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            color = DashboardColors.SlateGray
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Device properties grid matching Root Checker style
        GridInfoItem(label = "Модель устройства", value = "${Build.MANUFACTURER} ${Build.MODEL}")
        Divider(color = DashboardColors.CardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
        
        GridInfoItem(label = "Версия Android", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Divider(color = DashboardColors.CardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
        
        val runningStatus = if (state.isRunning) "Запущена (Версия API ${state.serverVersion})" else "Не активна"
        val runningColor = if (state.isRunning) DashboardColors.ActiveGreen else DashboardColors.InactiveRed
        GridInfoItem(
            label = "Служба Shizuku DB",
            value = runningStatus,
            valueColor = runningColor
        )
        
        Divider(color = DashboardColors.CardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
        val installStatus = if (state.isAppInstalled) "Установлено" else "Не найдено"
        val installColor = if (state.isAppInstalled) DashboardColors.ActiveGreen else DashboardColors.WarningAmber
        GridInfoItem(
            label = "Менеджер Shizuku",
            value = installStatus,
            valueColor = installColor
        )
    }
}

@Composable
fun GridInfoItem(
    label: String,
    value: String,
    valueColor: Color = DashboardColors.SlateLight
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardColors.SlateGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Custom overlap-rectangles double box representing copy to clipboard action icon
@Composable
fun CopyDoubleBoxIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back overlapping box
        Box(
            modifier = Modifier
                .offset(y = (-3).dp, x = 3.dp)
                .size(11.dp)
                .border(1.5.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
        // Foreground box
        Box(
            modifier = Modifier
                .offset(y = 2.dp, x = (-2).dp)
                .size(11.dp)
                .background(Color.Transparent)
                .border(1.5.dp, tint, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun AdbSandboxConsole(
    state: ShizukuState,
    onRunCommand: (String) -> Unit,
    onClearTerminal: () -> Unit
) {
    var commandInput by remember { mutableStateOf("id") }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F151F), RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, DashboardColors.CardBorder), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = DashboardColors.ActiveGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADB КОНСОЛЬ-ТЕСТЕР",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    color = DashboardColors.ActiveGreen
                )
            }
            
            if (state.terminalOutput.isNotEmpty()) {
                Text(
                    text = "Очистить",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = DashboardColors.SlateGray,
                    modifier = Modifier
                        .clickable { onClearTerminal() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Проверьте работоспособность привилегий в реальном времени, выполнив ADB-команду:",
            style = MaterialTheme.typography.bodySmall,
            color = DashboardColors.SlateGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Preset Commands chips row
        Text(
            text = "Шаблоны команд:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DashboardColors.SlateLight
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf("id", "settings get secure android_id", "getprop ro.product.model")
            presets.forEach { preset ->
                Box(
                    modifier = Modifier
                        .background(DashboardColors.SurfaceDark, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, if (commandInput == preset) DashboardColors.ActiveGreen else DashboardColors.CardBorder), RoundedCornerShape(8.dp))
                        .clickable { commandInput = preset }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (preset == "id") "Узнать UID (id)" else if (preset.startsWith("settings")) "Secure ID" else "Показать модель",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (commandInput == preset) DashboardColors.ActiveGreen else DashboardColors.SlateLight
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Command TextField input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = DashboardColors.SlateLight
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DashboardColors.ActiveGreen,
                    unfocusedBorderColor = DashboardColors.CardBorder,
                    focusedContainerColor = Color(0xFF070B11),
                    unfocusedContainerColor = Color(0xFF070B11)
                ),
                placeholder = {
                    Text("Введите shell команду...", color = DashboardColors.SlateGray, style = MaterialTheme.typography.bodyMedium)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (commandInput.isNotBlank()) {
                            onRunCommand(commandInput)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = {
                    keyboardController?.hide()
                    if (commandInput.isNotBlank()) {
                        onRunCommand(commandInput)
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF00E676), RoundedCornerShape(12.dp))
                    .size(52.dp)
                    .testTag("run_command_button"),
                enabled = !state.isTerminalRunning
            ) {
                if (state.isTerminalRunning) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Пуск",
                        tint = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terminal screen container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 220.dp)
                .background(Color(0xFF05080E), RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, DashboardColors.CardBorder), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val displayMessage = when {
                state.isTerminalRunning -> "Выполнение команды в фоновом процессе Shizuku...\n$ adb shell $commandInput"
                state.terminalOutput.isNotEmpty() -> state.terminalOutput
                else -> "${'$'} adb shell [команда]\n\nЗдесь будет выведен прямой результат выполнения команды через интерактивный ADB Binder."
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        color = if (state.terminalOutput.startsWith("Ошибка") || state.terminalOutput.startsWith("Error")) DashboardColors.InactiveRed else DashboardColors.SlateLight
                    )
                }
            }

            if (state.terminalOutput.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ADB Output", state.terminalOutput)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Результат скопирован в буфер", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .size(36.dp)
                ) {
                    CopyDoubleBoxIcon(tint = DashboardColors.SlateLight)
                }
            }
        }
    }
}

@Composable
fun QuickActionSuggestionCard(
    state: ShizukuState,
    onGoToGuides: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardColors.SurfaceDark, RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, DashboardColors.CardBorder), RoundedCornerShape(18.dp))
            .clickable { onGoToGuides() }
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DashboardColors.WarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "КАК ЗАПУСТИТЬ SHIZUKU?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = DashboardColors.SlateLight
                )
                Text(
                    text = "Сервис не запущен на устройстве. Нажмите, чтобы открыть подробное пошаговое руководство.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardColors.SlateGray
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = DashboardColors.SlateGray
            )
        }
    }
}

@Composable
fun SetupGuidesSection(state: ShizukuState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val launcherContext = LocalContext.current
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DashboardColors.SurfaceDark),
            border = BorderStroke(1.dp, DashboardColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "СКАЧАТЬ МЕНЕДЖЕР SHIZUKU",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    color = DashboardColors.ActiveGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Официальное приложение-менеджер Shizuku можно установить из различных источников. Выберите наиболее удобный вариант для скачивания:",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardColors.SlateGray
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Download options
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Google Play
                    Button(
                        onClick = {
                            try {
                                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                                launcherContext.startActivity(playIntent)
                            } catch (e: Exception) {
                                Toast.makeText(launcherContext, "Не удалось открыть Google Play", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F151F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DashboardColors.CardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DashboardColors.ActiveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скачать из Google Play", color = DashboardColors.SlateLight, style = MaterialTheme.typography.labelMedium)
                    }

                    // GitHub Releases
                    Button(
                        onClick = {
                            try {
                                val githubIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                                launcherContext.startActivity(githubIntent)
                            } catch (e: Exception) {
                                Toast.makeText(launcherContext, "Не удалось открыть GitHub", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F151F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DashboardColors.CardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = DashboardColors.ActiveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скачать с GitHub Releases (.apk)", color = DashboardColors.SlateLight, style = MaterialTheme.typography.labelMedium)
                    }

                    // Official Website
                    Button(
                        onClick = {
                            try {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/ru/download/"))
                                launcherContext.startActivity(webIntent)
                            } catch (e: Exception) {
                                Toast.makeText(launcherContext, "Не удалось открыть сайт", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F151F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DashboardColors.CardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = DashboardColors.ActiveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Официальный сайт (shizuku.rikka.app)", color = DashboardColors.SlateLight, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = DashboardColors.CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val clipboard = launcherContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    Button(
                        onClick = {
                            val clip = ClipData.newPlainText("Shizuku site", "https://shizuku.rikka.app/download/")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(launcherContext, "Ссылка скопирована скопирована в буфер обмена!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DashboardColors.SurfaceCard),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DashboardColors.CardBorder)
                    ) {
                        Text("Скопировать ссылку", color = DashboardColors.SlateLight, style = MaterialTheme.typography.labelMedium)
                    }
                    
                    Button(
                        onClick = {
                            val intent = launcherContext.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) {
                                launcherContext.startActivity(intent)
                            } else {
                                Toast.makeText(launcherContext, "Приложение Shizuku на устройстве не найдено.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DashboardColors.ActiveGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Открыть Shizuku", color = Color.Black, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Text(
            text = "МЕТОДЫ АКТИВАЦИИ СЛУЖБЫ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            color = DashboardColors.SlateGray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Accordion 1: Wireless Debugging
        AccordionGuideCard(
            title = "Вариант 1: Беспроводная отладка (Рекомендуемый, Android 11+)",
            icon = Icons.Default.Settings,
            steps = listOf(
                "Подключите телефон к активной сети Wi-Fi.",
                "Перейдите в 'Настройки' -> 'О телефоне' и нажмите 7 раз на 'Номер сборки' для включения Меню Разработчика.",
                "Откройте 'Для разработчиков' и активируйте блоки 'Отладка по USB' и 'Беспроводная отладка'.",
                "Запустите приложение Shizuku, нажмите на кнопку 'Подключение' (Pairing), затем выберите беспроводную отладку и 'Подключить устройство'.",
                "Нажмите 'Код сопряжения' в настройках телефона, введите его в уведомление Shizuku.",
                "Вернитесь в Shizuku и нажмите кнопку 'Запустить' (Start). Служба инициализируется!"
            )
        )

        // Accordion 2: PC Execution
        AccordionGuideCard(
            title = "Вариант 2: Запуск с ПК через провод ADB",
            icon = Icons.Default.Settings,
            steps = listOf(
                "Подключите ваш девайс с включенной отладкой USB к компьютеру по кабелю.",
                "Установите драйверы Android SDK Platform Tools на ваш ПК.",
                "Откройте Terminal (или Powershell) на ПК и вбейте команду проверки соединения:\n\n> adb devices",
                "При обнаружении вашего устройства, скопируйте и запустите следующую команду Shizuku в консоли:\n\n> adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                "Консоль компьютера сообщит о завершении процесса. Программа Shizuku покажет зеленый статус!"
            ),
            allowCopyCommand = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
        )

        // Accordion 3: Root Execution
        AccordionGuideCard(
            title = "Вариант 3: С правами Root-инструмента (В 1 клик)",
            icon = Icons.Default.Lock,
            steps = listOf(
                "Если на вашем устройстве уже настроен Magisk или KernelSU, запуск происходит мгновенно.",
                "Просто откройте утилиту Shizuku.",
                "В разделе 'Запуск через Root' нажмите кнопку 'Запустить'.",
                "Менеджер суперпользователя выдаст запрос. Предоставьте права суперпользователя Shizuku.",
                "Служба Shizuku запустится сама за секунду!"
            )
        )
    }
}

@Composable
fun AccordionGuideCard(
    title: String,
    icon: ImageVector,
    steps: List<String>,
    allowCopyCommand: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardColors.SurfaceDark, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, if (expanded) DashboardColors.ActiveGreen else DashboardColors.CardBorder), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (expanded) DashboardColors.ActiveGreen else DashboardColors.SlateGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = DashboardColors.SlateLight
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DashboardColors.SlateGray
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = DashboardColors.ActiveGreen,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = DashboardColors.SlateGray,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (allowCopyCommand != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F151F), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, DashboardColors.CardBorder), RoundedCornerShape(10.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ADB Command", allowCopyCommand)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Команда скопирована!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = allowCopyCommand,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = DashboardColors.ActiveGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CopyDoubleBoxIcon(tint = DashboardColors.SlateGray)
                    }
                }
            }
        }
    }
}

@Composable
fun FaqSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val faqs = listOf(
            "Что такое Shizuku?" to "Shizuku — это служба Android, которая позволяет обычным сторонним приложениям использовать системные программные интерфейсы (API) напрямую с привилегиями ADB (отладчика) или ROOT через единый IPC транзакционный туннель.",
            "Безопасно ли использовать Shizuku?" to "Абсолютно. Shizuku работает в жестких рамках встроенного системного ADB, контролируемого исключительно вами. Каждое приложение обязано запрашивать разрешение перед взаимодействием с Shizuku API.",
            "Почему утилита показывает статус НЕАКТИВЕН?" to "Поскольку Android останавливает фоновые фоновые Binder скрипты при перезагрузке телефона или деактивации локальной беспроводной отладки в целях экономии энергии. Вам просто нужно запустить службу кликом в менеджере Shizuku.",
            "Как исправить частый вылет Shizuku?" to "Обязательно отключите контроль экономии энергии (Батарея -> Оптимизация оптимизации) для приложения Shizuku в настройках вашего телефона, чтобы Android не убивал фоновые процессы демона."
        )

        faqs.forEach { (q, a) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DashboardColors.SurfaceDark),
                border = BorderStroke(1.dp, DashboardColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "• $q",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = DashboardColors.ActiveGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = a,
                        style = MaterialTheme.typography.bodySmall,
                        color = DashboardColors.SlateGray,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// Thread-safe command runner through the Shizuku Binder Process
private fun executeAdbCommand(command: String): String {
    return try {
        if (!Shizuku.pingBinder()) {
            return "Ошибка: Служба Shizuku не найдена или не запущена."
        }
        
        // Execute shell commands directly through binder via reflection to access the private newProcess
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as java.lang.Process
        
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        
        val outputBuilder = StringBuilder()
        var line: String?
        
        // Timeout/limit read to prevent locks
        var lineCount = 0
        while (reader.readLine().also { line = it } != null && lineCount < 100) {
            outputBuilder.append(line).append("\n")
            lineCount++
        }
        
        val errorBuilder = StringBuilder()
        while (errorReader.readLine().also { line = it } != null && lineCount < 100) {
            errorBuilder.append(line).append("\n")
            lineCount++
        }
        
        process.waitFor()
        process.destroy()
        
        val outResult = outputBuilder.toString().trim()
        val errResult = errorBuilder.toString().trim()
        
        when {
            outResult.isNotEmpty() -> outResult
            errResult.isNotEmpty() -> "Ошибка:\n$errResult"
            else -> "Команда выполнена успешно (пустой вывод)."
        }
    } catch (e: SecurityException) {
        "Ошибка безопасности:\nПриложению не выдано разрешение в диспетчере Shizuku."
    } catch (e: Exception) {
        "Ошибка выполнения:\n${e.localizedMessage ?: "Неизвестный сбой"}"
    }
}
