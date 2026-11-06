package com.maz.blurrr

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Background = Color(0xFF09090D)
private val SurfaceOne = Color(0xFF14141B)
private val SurfaceTwo = Color(0xFF1C1C25)
private val Stroke = Color(0xFF2B2B36)
private val Muted = Color(0xFF9292A0)
private val White = Color(0xFFF7F7FA)
private val Red = Color(0xFFFF4D5A)
private val Green = Color(0xFF45D6A0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(9, 9, 13)
        window.decorView.systemUiVisibility = 0
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red,
                    background = Background,
                    surface = SurfaceOne,
                    onPrimary = Color.White,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                MazApp()
            }
        }
    }
}

@Composable
private fun MazApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolFile = remember { File(context.filesDir, "tools/tiktube/ixxlSave.html") }
    var selectedTab by remember { mutableStateOf("Tools") }
    var toolInstalled by remember { mutableStateOf(toolFile.exists() && toolFile.length() > 0L) }
    var installing by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var activeTool by remember { mutableStateOf(false) }

    if (activeTool) {
        TikTubeScreen(
            htmlFile = toolFile,
            onBack = { activeTool = false },
            context = context
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                        .background(Red),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MAZ", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Your tools, one place.", color = Muted, fontSize = 12.sp)
                }
                Surface(
                    color = Color(0xFF1D2827),
                    shape = RoundedCornerShape(30.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29473F))
                ) {
                    Text("BETA", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(30.dp))
            Text("YOUR WORKSPACE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(7.dp))
            Text("Everything you need.", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Text("Download a tool once, then open it whenever you need it.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(SurfaceOne).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf("Tools", "Apps", "Socials").forEach { tab ->
                    val active = selectedTab == tab
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (active) SurfaceTwo else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tab, color = if (active) White else Muted,
                            fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab-content"
            ) { tab ->
                when (tab) {
                    "Tools" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TOOLS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                            Text("1 TOOL", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                        TikTubeCard(
                            installed = toolInstalled,
                            installing = installing,
                            onAction = {
                                if (toolInstalled) {
                                    activeTool = true
                                } else if (!installing) {
                                    installing = true
                                    installError = null
                                    scope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                toolFile.parentFile?.mkdirs()
                                                context.assets.open("ixxlSave.html").use { input ->
                                                    toolFile.outputStream().use { output -> input.copyTo(output) }
                                                }
                                            }
                                        }
                                        installing = false
                                        if (result.isSuccess && toolFile.exists() && toolFile.length() > 0L) {
                                            toolInstalled = true
                                        } else {
                                            installError = result.exceptionOrNull()?.message ?: "Couldn't install TikTube. Try again."
                                        }
                                    }
                                }
                            }
                        )
                        if (installError != null) {
                            Text(installError!!, color = Color(0xFFFF8585), fontSize = 12.sp)
                        }
                    }
                    "Apps" -> EmptyState(
                        symbol = "▦",
                        title = "Your apps will live here",
                        detail = "Installed MAZ apps will show up in this space."
                    )
                    else -> EmptyState(
                        symbol = "◎",
                        title = "Your socials, together",
                        detail = "Social shortcuts and connected spaces are coming later."
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            PersonalToolCard()
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Stroke))
                Text("  MAZ  ", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Stroke))
            }
            Spacer(Modifier.height(16.dp))
            Text("Built to keep your tools simple.", color = Muted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TikTubeCard(
    installed: Boolean,
    installing: Boolean,
    onAction: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
            .background(SurfaceOne)
            .border(1.dp, Stroke, RoundedCornerShape(23.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF29202A)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFFFF5664), fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("TikTube", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("YouTube + TikTok tool", color = Muted, fontSize = 12.sp)
            }
            Surface(
                color = if (installed) Color(0xFF17352D) else Color(0xFF29202A),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    if (installed) "INSTALLED" else "READY TO INSTALL",
                    color = if (installed) Green else Color(0xFFFF9A9F),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(17.dp))
        Text(
            "Paste a video link, view available formats, and save content you have permission to download.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(17.dp))
        Button(
            onClick = onAction,
            enabled = !installing,
            modifier = Modifier.fillMaxWidth().height(49.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red, disabledContainerColor = Color(0xFF56333A))
        ) {
            if (installing) {
                CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("Installing…", fontWeight = FontWeight.Bold)
            } else {
                Text(if (installed) "Open TikTube  →" else "↓  Download TikTube", fontWeight = FontWeight.Bold)
            }
        }
        if (!installed) {
            Spacer(Modifier.height(9.dp))
            Text("Free · Stored inside MAZ · Open anytime", color = Muted,
                fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun PersonalToolCard() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF111117))
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceTwo),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Muted, fontSize = 25.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Personal tool", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Add your own tools to MAZ", color = Muted, fontSize = 11.sp)
        }
        Surface(color = Color(0xFF252530), shape = RoundedCornerShape(30.dp)) {
            Text("COMING SOON", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun EmptyState(symbol: String, title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(SurfaceOne).border(1.dp, Stroke, RoundedCornerShape(22.dp))
            .padding(vertical = 35.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(SurfaceTwo), contentAlignment = Alignment.Center) {
            Text(symbol, color = Muted, fontSize = 26.sp)
        }
        Spacer(Modifier.height(15.dp))
        Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TikTubeScreen(htmlFile: File, onBack: () -> Unit, context: Context) {
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)) {
                Text("‹  MAZ", color = White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(5.dp))
            Column(Modifier.weight(1f)) {
                Text("TikTube", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("YouTube & TikTok", color = Muted, fontSize = 10.sp)
            }
            Surface(color = Color(0xFF17352D), shape = RoundedCornerShape(30.dp)) {
                Text("INSTALLED", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxSize().background(Background)) {
            key(htmlFile.lastModified()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        val webView = WebView(viewContext)
                        webView.setBackgroundColor(android.graphics.Color.rgb(9, 9, 13))
                        webView.isVerticalScrollBarEnabled = false
                        webView.isHorizontalScrollBarEnabled = false
                        webView.settings.javaScriptEnabled = true
                        webView.settings.domStorageEnabled = true
                        webView.settings.javaScriptCanOpenWindowsAutomatically = true
                        webView.settings.setSupportMultipleWindows(false)
                        webView.settings.allowFileAccess = false
                        webView.settings.allowContentAccess = true
                        webView.settings.loadsImagesAutomatically = true
                        webView.webChromeClient = WebChromeClient()
                        webView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                return if (url.startsWith("https://appassets.androidplatform.net/")) {
                                    false
                                } else if (url.startsWith("https://") || url.startsWith("http://")) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                    true
                                } else true
                            }
                        }
                        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            runCatching {
                                val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                                val request = DownloadManager.Request(Uri.parse(url))
                                    .setMimeType(mimeType)
                                    .addRequestHeader("User-Agent", userAgent)
                                    .setTitle(filename)
                                    .setDescription("Downloading with TikTube")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                manager.enqueue(request)
                            }
                        })
                        val html = htmlFile.readText(Charsets.UTF_8)
                        webView.loadDataWithBaseURL(
                            "https://appassets.androidplatform.net/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webView
                    },
                    update = {}
                )
            }
        }
    }
}
