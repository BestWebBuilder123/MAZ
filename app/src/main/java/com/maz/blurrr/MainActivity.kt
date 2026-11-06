package com.maz.blurrr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val bg = Color(0xFF1D1B1E)
private val panel = Color(0xFF29272A)
private val panel2 = Color(0xFF353236)
private val lime = Color(0xFFD5FF35)
private val muted = Color(0xFF969197)
data class Project(val name:String,val ratio:String,val resolution:String,val fps:Int)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent {
   var projects by remember { mutableStateOf(loadProjects()) }
   var screen by remember { mutableStateOf("home") }
   var current by remember { mutableStateOf<Project?>(null) }
   var newDialog by remember { mutableStateOf(false) }
   var accent by remember { mutableStateOf(lime) }
   var updateText by remember { mutableStateOf("Checking GitHub…") }
   var updateUrl by remember { mutableStateOf<String?>(null) }
   val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri -> if(uri!=null) contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   LaunchedEffect(Unit){ updateUrl=latestUpdate(); updateText=if(updateUrl==null) "No newer release found." else "A newer version is available." }
   MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=panel)){
    Surface(Modifier.fillMaxSize(),color=bg){
     when(screen){
      "editor" -> EditorScreen(current?:Project("Untitled","16:9","1080p",30),{screen="home"},{picker.launch(arrayOf("video/*","audio/*","image/*"))})
      "settings" -> SettingsScreen(accent,{accent=it},updateText,{updateUrl?.let { url -> startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }},{screen="home"})
      else -> HomeScreen(projects,{newDialog=true},{current=it;screen="editor"},{screen="settings"},{picker.launch(arrayOf("video/*","audio/*","image/*"))})
     }
     if(newDialog) NewProjectDialog({newDialog=false}){ p -> projects=listOf(p)+projects; saveProjects(projects); current=p; newDialog=false; screen="editor" }
    }
   }
  }
 }
 private fun loadProjects():List<Project> = runCatching {
  val a=JSONArray(getSharedPreferences("blurrr",MODE_PRIVATE).getString("projects","[]"))
  (0 until a.length()).map{val o=a.getJSONObject(it);Project(o.getString("name"),o.optString("ratio","16:9"),o.optString("resolution","1080p"),o.optInt("fps",30))}
 }.getOrDefault(emptyList())
 private fun saveProjects(list:List<Project>){val a=JSONArray();list.forEach{a.put(JSONObject().put("name",it.name).put("ratio",it.ratio).put("resolution",it.resolution).put("fps",it.fps))};getSharedPreferences("blurrr",MODE_PRIVATE).edit().putString("projects",a.toString()).apply()}
 private suspend fun latestUpdate():String?=withContext(Dispatchers.IO){runCatching{
  val c=URL("https://api.github.com/repos/BestWebBuilder123/MAZ/releases/latest").openConnection() as HttpURLConnection
  c.connectTimeout=8000;c.readTimeout=8000
  val j=JSONObject(c.inputStream.bufferedReader().use{it.readText()});c.disconnect()
  val tag=j.optString("tag_name").removePrefix("v")
  val current=packageManager.getPackageInfo(packageName,0).versionName?: "1.0.0"
  fun nums(s:String)=s.split(".").map{it.toIntOrNull()?:0}
  val newer=(0..2).firstNotNullOfOrNull{ i -> val a=nums(tag).getOrElse(i){0};val b=nums(current).getOrElse(i){0};if(a!=b) a>b else null }?:false
  if(newer) j.optJSONArray("assets")?.let{a->(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.optString("name")=="blurrr-android.apk"}?.optString("browser_download_url")} else null
 }.getOrNull()}
}

@Composable private fun HomeScreen(projects:List<Project>,onNew:()->Unit,onOpen:(Project)->Unit,onSettings:()->Unit,onImport:()->Unit){
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Default.AddBox,null,tint=Color.White);Text("Blurrr",fontSize=20.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Icon(Icons.Default.SportsEsports,null,tint=Color.White);Icon(Icons.Default.Diamond,null,tint=Color.Cyan)}
  }
  Column(Modifier.fillMaxWidth().padding(horizontal=48.dp).clip(RoundedCornerShape(28.dp)).background(panel2).clickable{onNew()}.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Icon(Icons.Default.MovieCreation,null,tint=lime,modifier=Modifier.size(52.dp));Text("Start Creating",fontWeight=FontWeight.Bold);Text("New Project / Tools",fontSize=11.sp,color=muted)
  }
  Spacer(Modifier.height(18.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){MiniTool("FPS Changer",Icons.Default.FastForward);MiniTool("V-FPS changer",Icons.Default.Speed);MiniTool("Anime",Icons.Default.Hd)}
  Spacer(Modifier.height(18.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Text("Your project",fontWeight=FontWeight.Bold);Spacer(Modifier.width(16.dp));Text("Cloud project",color=muted);Spacer(Modifier.weight(1f));Button(onClick=onImport,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF55F58A),contentColor=Color.Black)){Icon(Icons.Default.Download,null);Text(" Import")}}
  LazyColumn(Modifier.weight(1f)){items(projects){p->Row(Modifier.fillMaxWidth().clickable{onOpen(p)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(panel2),contentAlignment=Alignment.Center){Icon(Icons.Default.Movie,null,tint=muted,modifier=Modifier.size(32.dp))}
   Column(Modifier.weight(1f).padding(start=12.dp)){Text(p.name,fontWeight=FontWeight.Bold);Text(p.ratio+" • "+p.fps+" FPS",color=muted,fontSize=11.sp);Text(p.resolution,color=muted,fontSize=11.sp)}
   Icon(Icons.Default.MoreHoriz,null,tint=muted)
  }}}
  Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceAround){Nav("Playground",Icons.Default.VideoLibrary,false){};Nav("Creation",Icons.Default.AddBox,true,onNew);Nav("My",Icons.Default.AccountCircle,false,onSettings)}
 }
}
@Composable private fun MiniTool(t:String,i:androidx.compose.ui.graphics.vector.ImageVector){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=Color.LightGray);Text(t,fontSize=10.sp,color=muted)}}
@Composable private fun Nav(t:String,i:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean,click:()->Unit){Column(Modifier.clickable{click()}.padding(horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=if(active)Color.White else muted);Text(t,fontSize=11.sp,color=if(active)Color.White else muted)}}

@Composable private fun NewProjectDialog(close:()->Unit,create:(Project)->Unit){
 var name by remember{mutableStateOf("New Project")};var ratio by remember{mutableStateOf("16:9")};var res by remember{mutableStateOf("1080p")};var fps by remember{mutableIntStateOf(30)}
 AlertDialog(onDismissRequest=close,containerColor=panel,title={Text("New Project")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  OutlinedTextField(name,{name=it},label={Text("Project name")},singleLine=true)
  Text("Choose frame",fontSize=12.sp,color=muted)
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Customize","16:9","9:16","1:1","4:5").forEach{FilterChip(ratio==it,{ratio=it},{Text(it)})}}
  Text("Resolution",fontSize=12.sp,color=muted)
  Row{listOf("480p","720p","1080p").forEach{FilterChip(res==it,{res=it},{Text(it)})}}
  Text("Select frame rate",fontSize=12.sp,color=muted)
  Row{listOf(24,30,60).forEach{FilterChip(fps==it,{fps=it},{Text(it.toString()+"F")})}}
 }},confirmButton={Button(onClick={create(Project(name.ifBlank{"New Project"},ratio,res,fps))},colors=ButtonDefaults.buttonColors(containerColor=lime,contentColor=Color.Black)){Text("+ New Project")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

@Composable private fun EditorScreen(project:Project,back:()->Unit,import:()->Unit){
 var chosen by remember{mutableStateOf("Video/Photo")}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Close,null,Modifier.clickable{back()},tint=Color.White);Text(project.resolution,color=Color.White);Icon(Icons.Default.FileDownload,null,tint=muted)}
  Box(Modifier.fillMaxWidth().weight(0.48f).padding(horizontal=12.dp).background(Color(0xFFF7F6F8)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Movie,null,tint=Color.LightGray,modifier=Modifier.size(40.dp));Text("No Display",color=Color.Gray);Text("Materials",fontSize=11.sp,color=Color.LightGray)}}
  Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceEvenly){Icon(Icons.Default.Undo,null,tint=muted);Icon(Icons.Default.Redo,null,tint=muted);Text("½",color=Color.White);Icon(Icons.Default.SkipPrevious,null,tint=muted);Icon(Icons.Default.PlayArrow,null,tint=Color.White);Icon(Icons.Default.SkipNext,null,tint=muted)}
  Column(Modifier.fillMaxWidth().weight(0.36f).background(Color(0xFF171619))){
   Text("00:00:00",Modifier.align(Alignment.CenterHorizontally).padding(8.dp),color=Color.White)
   Box(Modifier.fillMaxWidth().weight(1f)){Column(Modifier.fillMaxWidth().padding(start=8.dp,top=16.dp)){Track("▣  Materials");Track("♫  Add music")};Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White).align(Alignment.TopCenter))}
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(7.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("Video/Photo","Music","Fx","Text","Shapes","3D").forEach{t->Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).background(if(chosen==t)panel2 else panel).clickable{chosen=t;if(t=="Video/Photo"||t=="Music")import()}.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Icon(when(t){"Music"->Icons.Default.MusicNote;"Fx"->Icons.Default.AutoAwesome;"Text"->Icons.Default.TextFields;"Shapes"->Icons.Default.Category;"3D"->Icons.Default.ViewInAr;else->Icons.Default.VideoLibrary},null,tint=Color.White);Text(t,fontSize=11.sp,color=muted)
   }}
  }
 }
}
@Composable private fun Track(t:String){Row(Modifier.fillMaxWidth().padding(vertical=3.dp).clip(RoundedCornerShape(6.dp)).background(panel).padding(10.dp)){Text(t,color=muted,fontSize=12.sp)}}
@Composable private fun SettingsScreen(accent:Color,setAccent:(Color)->Unit,update:String,download:()->Unit,back:()->Unit){
 Column(Modifier.fillMaxSize().padding(20.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ArrowBack,null,Modifier.clickable{back()}.padding(end=12.dp));Text("My",fontSize=22.sp,fontWeight=FontWeight.Bold)}
  Spacer(Modifier.height(28.dp));Text("App theme",fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf(lime,Color(0xFF7B8CFF),Color(0xFFFF79B8),Color(0xFF57D8C3),Color.White).forEach{Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(it).clickable{setAccent(it)})}}
  Spacer(Modifier.height(28.dp));Text("App updates",fontWeight=FontWeight.Bold);Text(update,color=muted);Button(onClick=download){Text("Open update download")}
 }
}
