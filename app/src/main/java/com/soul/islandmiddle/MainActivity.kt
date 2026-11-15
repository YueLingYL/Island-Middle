package com.soul.islandmiddle

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.OnBackCompletedFallback
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideNavigationEventOwner {
                IslandMiddleApp()
            }
        }
    }
}

@Composable
private fun ProvideNavigationEventOwner(content: @Composable () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val owner = remember {
        val dispatcher = NavigationEventDispatcher(
            onBackCompletedFallback = OnBackCompletedFallback {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        )
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
    }
    DisposableEffect(owner) {
        val dispatcher = owner.navigationEventDispatcher
        val input = if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDefaultInput(activity.onBackInvokedDispatcher)
        } else {
            null
        }
        if (input != null) dispatcher.addInput(input)
        onDispose {
            if (input != null) dispatcher.removeInput(input)
            dispatcher.dispose()
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        content()
    }
}

private sealed class DialogInfo {
    data class NoRoot(val message: String) : DialogInfo()
    data class ConfirmKill(val rootInfo: String) : DialogInfo()
}

private enum class Tab(val title: String, val subtitle: String) {
    Home("主页", "开关、状态与强停操作"),
    About("关于", "应用信息、原理与说明")
}

@Composable
private fun IslandMiddleApp() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MiuixTheme {
        val context = view.context
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("config", 0) }

        var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
        var checking by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("") }
        var dialog by remember { mutableStateOf<DialogInfo?>(null) }
        var page by remember { mutableIntStateOf(0) }

        fun onEnabledChange(value: Boolean) {
            enabled = value
            prefs.edit().putBoolean("enabled", value).apply()
            statusText = if (value) "已开启，强停 SystemUI 后生效" else "已关闭，强停 SystemUI 后生效"
        }

        fun startCheckSu() {
            if (checking) return
            checking = true
            statusText = "正在检测 su…"
            scope.launch {
                val result = withContext(Dispatchers.IO) { checkSu() }
                checking = false
                dialog = if (result.first) {
                    statusText = "已检测到 root"
                    DialogInfo.ConfirmKill(result.second)
                } else {
                    statusText = "未检测到可用 su"
                    DialogInfo.NoRoot(result.second)
                }
            }
        }

        fun doKill() {
            statusText = "正在强停 SystemUI…"
            scope.launch {
                val r = withContext(Dispatchers.IO) { killSystemUi() }
                statusText =
                    if (r.first) "已强停 SystemUI，Hook 将重载" else "强停失败：${r.second}"
                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
            }
        }

        val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 600
        val tab = Tab.entries[page]
        val info = dialog

        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = tab.title,
                    subtitle = tab.subtitle
                )
            },
            bottomBar = {
                if (!isLargeScreen) {
                    NavigationBar {
                        Tab.entries.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick = { page = index },
                                icon = tabIcon(item),
                                label = item.title
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLargeScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val railState = rememberNavigationRailState()
                        NavigationRail(state = railState) {
                            Tab.entries.forEachIndexed { index, item ->
                                NavigationRailItem(
                                    selected = page == index,
                                    onClick = { page = index },
                                    icon = tabIcon(item),
                                    label = item.title
                                )
                            }
                        }
                        PageBody(
                            page = page,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            enabled = enabled,
                            onEnabledChange = ::onEnabledChange,
                            checking = checking,
                            statusText = statusText,
                            onStartCheck = ::startCheckSu
                        )
                    }
                } else {
                    PageBody(
                        page = page,
                        modifier = Modifier.fillMaxSize(),
                        enabled = enabled,
                        onEnabledChange = ::onEnabledChange,
                        checking = checking,
                        statusText = statusText,
                        onStartCheck = ::startCheckSu
                    )
                }
            }

            OverlayDialog(
                show = info != null,
                title = when (info) {
                    is DialogInfo.NoRoot -> "未检测到 Root"
                    is DialogInfo.ConfirmKill -> "已检测到 Root"
                    null -> ""
                },
                summary = when (info) {
                    is DialogInfo.NoRoot -> info.message
                    is DialogInfo.ConfirmKill ->
                        "将执行 su -c killall com.android.systemui\n确认强停吗？\n\n${info.rootInfo}"
                    null -> ""
                },
                onDismissRequest = { dialog = null }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info is DialogInfo.ConfirmKill) {
                        TextButton(text = "取消", onClick = { dialog = null })
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            text = "强停",
                            onClick = {
                                dialog = null
                                doKill()
                            }
                        )
                    } else {
                        TextButton(text = "知道了", onClick = { dialog = null })
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Home -> MiuixIcons.Home
    Tab.About -> MiuixIcons.Info
}

@Composable
private fun PageBody(
    page: Int,
    modifier: Modifier,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    checking: Boolean,
    statusText: String,
    onStartCheck: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page"
            ) { target ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (target) {
                        0 -> HomePage(
                            enabled = enabled,
                            onEnabledChange = onEnabledChange,
                            checking = checking,
                            statusText = statusText,
                            onStartCheck = onStartCheck
                        )
                        else -> AboutPage()
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (title != null) {
        SmallTitle(text = title)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(top = 6.dp)
                .background(valueColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomePage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    checking: Boolean,
    statusText: String,
    onStartCheck: () -> Unit
) {
    SectionCard(title = "开关") {
        SwitchPreference(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            title = "启用 Hook",
            summary = if (enabled) "保持开启，强停或重启 SystemUI 后生效"
            else "已停用，下次 SystemUI 启动不再注入",
            insideMargin = PaddingValues(20.dp, 10.dp)
        )
    }

    SectionCard(title = "状态") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            StatusRow(
                label = "Hook 开关",
                value = if (enabled) "已启用" else "已停用",
                valueColor = if (enabled) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            HorizontalDivider()
            StatusRow(
                label = "操作结果",
                value = statusText.ifEmpty { "暂无" },
                valueColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    SectionCard(title = "快捷操作") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "以 root 强停 SystemUI，使模块 Hook 立即重新加载。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onStartCheck,
                enabled = !checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (checking) "检测 su 中…" else "强停 SystemUI",
                    fontSize = MiuixTheme.textStyles.title3.fontSize
                )
            }
        }
    }

    SectionCard(title = "生效方式") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            numberedItem("1", "保持「启用 Hook」开关开启")
            numberedItem("2", "强停 SystemUI 后 Hook 重载，或等待系统自动重启")
            numberedItem("3", "LSPosed 作用域须勾选 SystemUI")
        }
    }

    SectionCard(title = "环境依赖") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            numberedItem("1", "LSPosed 已激活本模块")
            numberedItem("2", "Hyper OS 3 及以上系统版本")
            numberedItem("3", "settings get global support_dynamic_island 返回 1")
            numberedItem("4", "强停按钮需要 su（KernelSU / Magisk 等）")
        }
    }
}

@Composable
private fun numberedItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = number,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val appInfo = remember(context) {
        val pm = context.packageManager
        val pkg = context.packageName
        val pi = pm.getPackageInfo(pkg, 0)
        val version = pi.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode
        Triple(version, versionCode, pkg)
    }

    SectionCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                text = "Island Middle",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "LSPosed 模块，让平板灵动岛走手机 / 折叠屏路径。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    SectionCard(title = "应用信息") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            StatusRow(
                label = "版本",
                value = "${appInfo.first} (${appInfo.second})",
                valueColor = MiuixTheme.colorScheme.primary
            )
            HorizontalDivider()
            StatusRow(
                label = "包名",
                value = appInfo.third,
                valueColor = MiuixTheme.colorScheme.primary
            )
            HorizontalDivider()
            StatusRow(
                label = "作用域",
                value = "com.android.systemui",
                valueColor = MiuixTheme.colorScheme.primary
            )
            HorizontalDivider()
            StatusRow(
                label = "框架",
                value = "LSPosed (Xposed API 82)",
                valueColor = MiuixTheme.colorScheme.primary
            )
        }
    }

    SectionCard(title = "原理") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            bulletItem("拦截 ro.build.characteristics（单参 / 两参），兜底写入 MiuiConfigs.IS_PAD = false")
            bulletItem("Hook 插件 CommonUtils.getIS_TABLET 与 isDynamicIslandPadNoPropOnce，强制返回 false")
            bulletItem("使灵动岛缩小态与展开态都走手机 / 折叠屏逻辑")
        }
    }

    SectionCard(title = "说明") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            bulletItem("仅处理给定 MiuiSystemUI / 插件逻辑，不改包签名")
            bulletItem("岛窗口由 miui.systemui.plugin 绘制，本模块在 SystemUI 进程内影响插件分支")
        }
    }
}

@Composable
private fun bulletItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .padding(top = 7.dp)
                .background(MiuixTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class CmdResult(val ok: Boolean, val out: String, val err: String)

private fun execSu(command: String, timeoutMs: Long = 5000): CmdResult {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread {
            try {
                process.inputStream.bufferedReader().use { out.append(it.readText()) }
            } catch (_: Throwable) {
            }
        }
        val tErr = Thread {
            try {
                process.errorStream.bufferedReader().use { err.append(it.readText()) }
            } catch (_: Throwable) {
            }
        }
        tOut.start()
        tErr.start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            tOut.join(500)
            tErr.join(500)
            return CmdResult(false, out.toString(), "超时 ${timeoutMs}ms，已终止")
        }
        tOut.join(1000)
        tErr.join(1000)
        CmdResult(process.exitValue() == 0, out.toString(), err.toString())
    } catch (t: Throwable) {
        CmdResult(false, "", t.message ?: t.toString())
    }
}

private fun checkSu(): Pair<Boolean, String> {
    val paths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/data/adb/ksu/bin/busybox", "/data/adb/magisk"
    )
    val pathHit = paths.filter { java.io.File(it).exists() }
    val r = execSu("id")
    val ok = r.ok && r.out.contains("uid=0")
    val info = buildString {
        append("uid: ").append(
            r.out.lineSequence().firstOrNull { it.contains("uid=") } ?: (r.err.ifBlank { "unknown" })
        )
        if (pathHit.isNotEmpty()) append("\n路径: ").append(pathHit.joinToString())
        if (r.err.isNotBlank()) append("\nerr: ").append(r.err.trim().take(120))
    }
    return ok to info
}

private fun killSystemUi(): Pair<Boolean, String> {
    val commands = listOf(
        "killall com.android.systemui",
        "pkill -9 -f com.android.systemui || kill -9 \$(pidof com.android.systemui)"
    )
    val logs = mutableListOf<String>()
    for (cmd in commands) {
        val r = execSu(cmd)
        logs += "[cmd] exit=${if (r.ok) 0 else 1} ${cmd}\nout=${r.out.trim().take(200)} err=${r.err.trim().take(200)}"
        if (r.ok) {
            return true to logs.joinToString("\n")
        }
        if (r.err.contains("超时", ignoreCase = true)) break
    }
    return false to logs.joinToString("\n")
}
