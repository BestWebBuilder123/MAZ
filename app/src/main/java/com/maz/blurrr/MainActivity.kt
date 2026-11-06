package com.maz.blurrr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val Ink = Color(0xFF111114)
private val Panel = Color(0xFF1B1B20)
private val Line = Color(0xFF34343B)
private val Muted = Color(0xFF9A9AA4)
private val Accent = Color(0xFFB8B5FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink, surface = Panel, primary = Accent,
                    onPrimary = Ink, onBackground = Color.White,
                    onSurface = Color.White, secondary = Accent
                )
            ) {
                MazApp(
                    openExternal = { value ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                        }
                    }
                )
            }
        }
    }
}

private data class PersonalTool(val name: String, val url: String)

@Composable
private fun MazApp(openExternal: (String) -> Unit) {
    var tab by remember { mutableStateOf("Tools") }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(loadTools()) }

    if (activeTool == "TikTube") {
        TikTubeScreen(onBack = { activeTool = null }, openExternal = openExternal)
        return
    }
    if (activeTool != null) {
        val custom = tools.firstOrNull { it.name == activeTool }
        CustomToolScreen(custom, onBack = { activeTool = null }, openExternal = openExternal)
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
                        Text(label, color = if (selected) Color.White else Muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            when (tab) {
                "Tools" -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        item {
                            ToolCard(
                                name = "TikTube",
                                description = "YouTube & TikTok video tool",
                                hint = "double tap to open",
                                icon = "▶",
                                onOpen = { activeTool = "TikTube" }
                            )
                        }
                        items(tools) { tool ->
                            ToolCard(
                                name = tool.name,
                                description = tool.url,
                                hint = "double tap to open",
                                icon = "✦",
                                onOpen = { activeTool = tool.name }
                            )
                        }
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
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(Line))
            Spacer(Modifier.height(0.dp))
            Box(
                Modifier.offset(y = (-1).dp).size(66.dp).clip(CircleShape)
                    .background(Panel).combinedClickable(onClick = { showAdd = true }),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Light)
            }
            Text("personal tool", color = Muted, fontSize = 13.sp)
        }
    }

    if (showAdd) {
        AddToolDialog(
            onDismiss = { showAdd = false },
            onSave = { name, url ->
                tools = tools + PersonalTool(name, url)
                saveTools(tools)
                tab = "Tools"
                showAdd = false
            }
        )
    }
}

@Composable
private fun ToolCard(
    name: String, description: String, hint: String, icon: String, onOpen: () -> Unit
) {
    var lastTap by remember { mutableLongStateOf(0L) }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Panel).border(BorderStroke(1.dp, Line), RoundedCornerShape(19.dp))
            .combinedClickable(
                onClick = {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastTap < 450L) onOpen()
                    lastTap = now
                },
                onLongClick = onOpen
            ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF303039)),
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

private fun Modifier.borderStroke(color: Color, radius: Int) =
    this.then(Modifier.background(Color.Transparent, RoundedCornerShape(radius.dp)))
        .padding(0.dp)

@Composable
private fun EmptySection(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AddToolDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add personal tool") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Tool name") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it }, label = { Text("Website URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.trim().contains(".") && !url.contains(" "),
                onClick = { onSave(name.trim(), normalizeUrl(url.trim())) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Panel
    )
}

@Composable
private fun CustomToolScreen(tool: PersonalTool?, onBack: () -> Unit, openExternal: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("‹  Back") }
        Spacer(Modifier.height(24.dp))
        Text(tool?.name ?: "Personal tool", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(tool?.url ?: "", color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { tool?.let { openExternal(it.url) } }) { Text("Open website") }
    }
}

@Composable
private fun TikTubeScreen(onBack: () -> Unit, openExternal: (String) -> Unit) {
    var platform by remember { mutableStateOf("YouTube") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Ink).padding(18.dp)) {
        TextButton(onClick = onBack) { Text("‹  Back to MAZ") }
        Spacer(Modifier.height(10.dp))
        Text("TikTube", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Save videos you own or have permission to download.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(4.dp)) {
            listOf("YouTube", "TikTok").forEach {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (platform == it) Color(0xFF3A3945) else Color.Transparent)
                    .combinedClickable(onClick = { platform = it; results = emptyList(); message = "" })
                    .padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(it, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = link, onValueChange = { link = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("$platform link") }, placeholder = { Text("Paste a video link") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                busy = true; message = "Fetching video information…"; results = emptyList()
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { fetchMedia(platform, link.trim()) }
                    busy = false
                    if (outcome.error != null) message = outcome.error
                    else {
                        title = outcome.title
                        author = outcome.author
                        results = outcome.items
                        message = if (results.isEmpty()) "No downloadable formats were returned." else "Choose an available format:"
                    }
                }
            },
            enabled = !busy && link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) { Text(if (busy) "Please wait…" else "Get video", fontWeight = FontWeight.Bold) }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(message, color = Muted, fontSize = 13.sp)
        }
        if (title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
            if (author.isNotBlank()) Text(author, color = Muted, fontSize = 12.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results) { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(item.first, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    TextButton(onClick = { openExternal(item.second) }) { Text("Open") }
                }
            }
        }
    }
}

private data class MediaOutcome(
    val title: String = "", val author: String = "",
    val items: List<Pair<String, String>> = emptyList(), val error: String? = null
)

private fun fetchMedia(platform: String, input: String): MediaOutcome {
    return try {
        val safe = when (platform) {
            "YouTube" -> {
                val id = Regex("""(?:v=|youtu\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})""")
                    .find(input)?.groupValues?.getOrNull(1)
                    ?: return MediaOutcome(error = "Enter a valid YouTube video link.")
                "https://www.youtube.com/watch?v=$id"
            }
            else -> {
                val uri = Uri.parse(input)
                if (uri.scheme !in listOf("http", "https") || !(uri.host ?: "").contains("tiktok.com"))
                    return MediaOutcome(error = "Enter a valid TikTok link.")
                input
            }
        }
        val api = if (platform == "YouTube")
            "https://btch-downloader-api-green.vercel.app/api/download/youtube?url=" + enc(safe)
        else "https://www.tikwm.com/api/?url=" + enc(safe) + "&hd=1"
        val json = requestJson(api)
        if (platform == "YouTube") {
            if (!json.optBoolean("success")) return MediaOutcome(error = "The video service could not retrieve this link. Try again later.")
            val normalized = json.optJSONObject("normalized") ?: JSONObject()
            val raw = json.optJSONObject("result") ?: JSONObject()
            val media = normalized.optJSONArray("media") ?: JSONArray()
            val items = (0 until media.length()).mapNotNull { i ->
                val m = media.optJSONObject(i) ?: return@mapNotNull null
                val url = m.optString("url")
                if (!validWebUrl(url)) null else (m.optString("label", "Format ${i + 1}") to url)
            }
            MediaOutcome(normalized.optString("title", raw.optString("title")),
                normalized.optString("author", raw.optString("author")), items)
        } else {
            if (json.optInt("code", -1) != 0) return MediaOutcome(error = "The TikTok service could not retrieve this link. Try again later.")
            val data = json.optJSONObject("data") ?: JSONObject()
            val items = mutableListOf<Pair<String, String>>()
            listOf("play" to "Standard video", "hdplay" to "HD video", "music" to "Audio").forEach { (key, label) ->
                val u = data.optString(key)
                if (validWebUrl(u) && items.none { it.second == u }) items.add(label to u)
            }
            val a = data.optJSONObject("author") ?: JSONObject()
            MediaOutcome(data.optString("title"), a.optString("unique_id").let { if (it.isBlank()) "" else "@$it" }, items)
        }
    } catch (_: Exception) {
        MediaOutcome(error = "Connection failed. The video service may be unavailable or blocked by your network.")
    }
}

private fun requestJson(address: String): JSONObject {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 18000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "MAZ-Android")
    }
    return try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        JSONObject(body)
    } finally { connection.disconnect() }
}

private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
private fun validWebUrl(value: String): Boolean =
    runCatching { val u = Uri.parse(value); u.scheme in listOf("http", "https") && !u.host.isNullOrBlank() }.getOrDefault(false)
private fun normalizeUrl(value: String) = if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"

private fun loadTools(): List<PersonalTool> = runCatching {
    val prefs = MazPrefs.instance ?: return@runCatching emptyList()
    val array = JSONArray(prefs.getString("personal_tools", "[]"))
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        PersonalTool(o.optString("name"), o.optString("url"))
    }
}.getOrDefault(emptyList())

private fun saveTools(tools: List<PersonalTool>) {
    val array = JSONArray()
    tools.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
    MazPrefs.instance?.edit()?.putString("personal_tools", array.toString())?.apply()
}

private object MazPrefs {
    var instance: android.content.SharedPreferences? = null
}
