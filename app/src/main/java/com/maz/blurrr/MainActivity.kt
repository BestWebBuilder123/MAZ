@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.maz.blurrr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private val Ink = Color(0xFF09090B)
private val Panel = Color(0xFF18181B)
private val Line = Color(0xFF34343B)
private val Muted = Color(0xFF9A9AA4)
private val Accent = Color(0xFFFF4545)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink,
                    surface = Panel,
                    primary = Accent,
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                MazApp(
                    openExternal = { address ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MazApp(openExternal: (String) -> Unit) {
    var tab by remember { mutableStateOf("Tools") }
    var activeTool by remember { mutableStateOf<String?>(null) }

    if (activeTool == "TikTube") {
        TikTubeScreen(onBack = { activeTool = null }, openExternal = openExternal)
        return
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(34.dp))
            Text("MAZ", fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp)
            Text("tools", color = Muted, fontSize = 13.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Panel).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Tools", "Apps", "Socials").forEach { label ->
                    val selected = tab == label
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                            .background(if (selected) Color(0xFF34343D) else Color.Transparent)
                            .combinedClickable(onClick = { tab = label })
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else Muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            when (tab) {
                "Tools" -> {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolCard(
                            name = "TikTube",
                            description = "YouTube & TikTok video tool",
                            hint = "tap to open",
                            icon = "▶",
                            onOpen = { activeTool = "TikTube" }
                        )
                    }
                }
                "Apps" -> EmptySection("No apps yet", "Your apps will appear here.")
                else -> EmptySection("No socials yet", "Your social shortcuts will appear here.")
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Ink).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp)
                    .height(1.dp).background(Line)
            )
            Box(
                Modifier.offset(y = (-1).dp).size(66.dp).clip(CircleShape)
                    .background(Panel),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 40.sp, color = Muted, fontWeight = FontWeight.Light)
            }
            Text("Personal tool · Coming soon", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToolCard(
    name: String,
    description: String,
    hint: String,
    icon: String,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Panel).border(BorderStroke(1.dp, Line), RoundedCornerShape(19.dp))
            .combinedClickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF303039)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 23.sp, color = Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Text(hint, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun EmptySection(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun TikTubeScreen(onBack: () -> Unit, openExternal: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        ) {
            Text("‹  Back to MAZ")
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                val webView = WebView(context)
                webView.setBackgroundColor(android.graphics.Color.rgb(9, 9, 11))
                webView.isVerticalScrollBarEnabled = false
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.javaScriptCanOpenWindowsAutomatically = false
                webView.settings.setSupportMultipleWindows(false)
                webView.settings.allowFileAccess = false
                webView.settings.allowContentAccess = true
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val address = request.url.toString()
                        if (address.startsWith("https://appassets.androidplatform.net/")) {
                            return false
                        }
                        if (address.startsWith("https://") || address.startsWith("http://")) {
                            openExternal(address)
                            return true
                        }
                        return true
                    }
                }

                val originalHtml = context.assets.open("ixxlSave.html")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val wrapper = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                      <style>
                        html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#09090b; }
                        iframe { display:block; width:100%; height:100%; border:0; background:#09090b; }
                      </style>
                    </head>
                    <body>
                      <iframe id="maziFrame" title="IxxlSave"></iframe>
                      <script>
                        const frame = document.getElementById("maziFrame");
                        frame.srcdoc = \${JSONObject.quote(originalHtml)};
                        frame.addEventListener("load", () => {
                          try {
                            frame.contentDocument.addEventListener("click", event => {
                              const link = event.target.closest("a[target='_blank']");
                              if (link) link.target = "_self";
                            }, true);
                          } catch (error) {}
                        });
                      </script>
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    wrapper,
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
