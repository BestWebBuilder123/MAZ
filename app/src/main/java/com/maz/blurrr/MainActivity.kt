package com.maz.blurrr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val bg=Color(0xFF1D1B1E)
private val panel=Color(0xFF29272A)
private val panel2=Color(0xFF353236)
private val lime=Color(0xFFD5FF35)
private val muted=Color(0xFF969197)
data class Project(val name:String,val ratio:String,val resolution:String,val fps:Int,val clips:List<String> = emptyList())

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent {
   var projects by remember { mutableStateOf(loadProjects()) }
   var screen by remember { mutableStateOf("home") }
   var current by remember { mutableStateOf<Project?>(null) }
   var newDialog by remember { mutableStateOf(false) }
   var accent by remember { mutableStateOf(getAccent()) }
   var updateText by remember { mutableStateOf("Checking GitHub…") }
   var updateUrl by remember { mutableStateOf<String?>(null) }
   val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris ->
    val p=current
    if(p!=null && uris.isNotEmpty()){
     uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
     val changed=p.copy(clips=p.clips+uris.map{it.toString()})
     current=changed
     projects=projects.map{if(it.name==p.name) changed else it}
     saveProjects(projects)
    }
   }
   LaunchedEffect(Unit){updateUrl=latestUpdate();updateText=if(updateUrl==null)"No newer release found (or GitHub has no published release)." else "A newer version is available."}
   MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=panel)){
    Surface(Modifier.fillMaxSize(),color=bg){
     when(screen){
      "editor" -> EditorScreen(current?:Project("Untitled","16:9","1080p",30),{screen="home"},{picker.launch(arrayOf("video/*","image/*","audio/*"))},{idx ->
       val p=current ?: return@EditorScreen
       val changed=p.copy(clips=p.clips.filterIndexed{ i,_->i!=idx })
       current=changed;projects=projects.map{if(it.name==p.name)changed else it};saveProjects(projects)
      })
      "settings" -> SettingsScreen(accent,{accent=it;saveAccent(it)},updateText,{updateUrl?.let{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it)))}},{screen="home"})
      else -> HomeScreen(projects,{newDialog=true},{current=it;screen="editor"},{screen="settings"},{picker.launch(arrayOf("video/*","image/*","audio/*"))})
     }
     if(newDialog)NewProjectDialog({newDialog=false}){p->projects=listOf(p)+projects;saveProjects(projects);current=p;newDialog=false;screen="editor"}
    }
   }
  }
 }
 private fun loadProjects():List<Project> = runCatching{
  val a=JSONArray(getSharedPreferences("blurrr",MODE_PRIVATE).getString("projects","[]"))
  (0 until a.length()).map{i->val o=a.getJSONObject(i);val c=o.optJSONArray("clips")?:JSONArray();Project(o.getString("name"),o.optString("ratio","16:9"),o.optString("resolution","1080p"),o.optInt("fps",30),(0 until c.length()).map{c.getString(it)})}
 }.getOrDefault(emptyList())
 private fun saveProjects(list:List<Project>){val a=JSONArray();list.forEach{p->val c=JSONArray();p.clips.forEach{c.put(it)};a.put(JSONObject().put("name",p.name).put("ratio",p.ratio).put("resolution",p.resolution).put("fps",p.fps).put("clips",c))};getSharedPreferences("blurrr",MODE_PRIVATE).edit().putString("projects",a.toString()).apply()}
 private fun getAccent():Color{val v=getSharedPreferences("blurrr",MODE_PRIVATE).getLong("accent",0xFFD5FF35);return Color(v)}
 private fun saveAccent(c:Color){getSharedPreferences("blurrr",MODE_PRIVATE).edit().putLong("accent",c.value.toLong()).apply()}
 private suspend fun latestUpdate():String?=withContext(Dispatchers.IO){runCatching{
  val c=URL("https://api.github.com/repos/BestWebBuilder123/MAZ/releases/latest").openConnection() as HttpURLConnection
  c.connectTimeout=8000;c.readTimeout=8000
  val j=JSONObject(c.inputStream.bufferedReader().use{it.readText()});c.disconnect()
  val tag=j.optString("tag_name").removePrefix("v");val current=packageManager.getPackageInfo(packageName,0).versionName?:"1.0.0"
  fun nums(s:String)=s.split(".").map{it.toIntOrNull()?:0}
  val a=nums(tag);val b=nums(current)
  val newer=(0..2).firstNotNullOfOrNull{i->val x=a.getOrElse(i){0};val y=b.getOrElse(i){0};if(x!=y)x>y else null}?:false
  if(newer)j.optJSONArray("assets")?.let{assets->(0 until assets.length()).map{assets.getJSONObject(it)}.firstOrNull{it.optString("name")=="blurrr-android.apk"}?.optString("browser_download_url")}else null
 }.getOrNull()}
}

@Composable private fun HomeScreen(projects:List<Project>,onNew:()->Unit,onOpen:(Project)->Unit,onSettings:()->Unit,onImport:()->Unit){
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AddBox,null,tint=Color.White);Text("Blurrr",fontSize=20.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Icon(Icons.Default.SportsEsports,null,tint=Color.White);Icon(Icons.Default.Diamond,null,tint=Color.Cyan)}}
  Column(Modifier.fillMaxWidth().padding(horizontal=48.dp).clip(RoundedCornerShape(28.dp)).background(panel2).clickable{onNew()}.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.MovieCreation,null,tint=lime,modifier=Modifier.size(52.dp));Text("Start Creating",fontWeight=FontWeight.Bold);Text("New Project / Tools",fontSize=11.sp,color=muted)}
  Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){MiniTool("FPS Changer",Icons.Default.FastForward);MiniTool("V-FPS changer",Icons.Default.Speed);MiniTool("Anime",Icons.Default.Hd)}
  Spacer(Modifier.height(18.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("Your project",fontWeight=FontWeight.Bold);Spacer(Modifier.width(16.dp));Text("Cloud project",color=muted);Spacer(Modifier.weight(1f));Button(onClick=onImport,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF55F58A),contentColor=Color.Black)){Icon(Icons.Default.Download,null);Text(" Import")}}
  LazyColumn(Modifier.weight(1f)){itemsIndexed(projects){_,p->Row(Modifier.fillMaxWidth().clickable{onOpen(p)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(panel2),contentAlignment=Alignment.Center){Icon(Icons.Default.Movie,null,tint=muted,modifier=Modifier.size(32.dp))}
   Column(Modifier.weight(1f).padding(start=12.dp)){Text(p.name,fontWeight=FontWeight.Bold);Text(p.ratio+" • "+p.fps+" FPS",color=muted,fontSize=11.sp);Text(p.resolution+" • "+p.clips.size+" media",color=muted,fontSize=11.sp)}
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
  OutlinedTextField(name,{name=it},label={Text("Project name")},singleLine=true);Text("Choose frame",fontSize=12.sp,color=muted)
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Customize","16:9","9:16","1:1","4:5").forEach{FilterChip(ratio==it,{ratio=it},{Text(it)})}}
  Text("Resolution",fontSize=12.sp,color=muted);Row{listOf("480p","720p","1080p").forEach{FilterChip(res==it,{res=it},{Text(it)})}}
  Text("Select frame rate",fontSize=12.sp,color=muted);Row{listOf(24,30,60).forEach{FilterChip(fps==it,{fps=it},{Text(it.toString()+"F")})}}
 }},confirmButton={Button(onClick={create(Project(name.ifBlank{"New Project"},ratio,res,fps))},colors=ButtonDefaults.buttonColors(containerColor=lime,contentColor=Color.Black)){Text("+ New Project")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

@Composable private fun EditorScreen(project:Project,back:()->Unit,import:()->Unit,remove:(Int)->Unit){
 var selected by remember{mutableIntStateOf(0)}
 var playing by remember{mutableStateOf(false)}
 val active=project.clips.getOrNull(selected)
 val isVideo=active?.let{it.contains("video",true)||it.endsWith(".mp4",true)||it.endsWith(".mov",true)||it.endsWith(".mkv",true)}?:false
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Close,null,Modifier.clickable{back()},tint=Color.White);Text(project.resolution+" • "+project.fps+" FPS",color=Color.White);Text("Export",color=muted)}
  Box(Modifier.fillMaxWidth().weight(0.48f).padding(horizontal=12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF080808)),contentAlignment=Alignment.Center){
   if(active==null)Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Movie,null,tint=Color.Gray,modifier=Modifier.size(40.dp));Text("No Display",color=Color.Gray);Text("Tap Video/Photo to import media",fontSize=11.sp,color=muted)}
   else if(isVideo)AndroidView(factory={ctx->VideoView(ctx).apply{setVideoURI(Uri.parse(active));setOnPreparedListener{it.isLooping=true;if(playing)start()}}},update={it.setVideoURI(Uri.parse(active));if(playing)it.start()else it.pause()},modifier=Modifier.fillMaxSize())
   else AndroidView(factory={ctx->ImageView(ctx).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageURI(Uri.parse(active))}},update={it.setImageURI(Uri.parse(active))},modifier=Modifier.fillMaxSize())
  }
  Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Default.Undo,null,tint=muted);Icon(Icons.Default.Redo,null,tint=muted);Text("½",color=Color.White)
   Icon(Icons.Default.SkipPrevious,null,tint=muted,modifier=Modifier.clickable{selected=(selected-1).coerceAtLeast(0);playing=false})
   Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.clickable{playing=!playing})
   Icon(Icons.Default.SkipNext,null,tint=muted,modifier=Modifier.clickable{selected=(selected+1).coerceAtMost((project.clips.size-1).coerceAtLeast(0));playing=false})
  }
  Column(Modifier.fillMaxWidth().weight(0.36f).background(Color(0xFF171619))){
   Text("00:00:00",Modifier.align(Alignment.CenterHorizontally).padding(8.dp),color=Color.White)
   Row(Modifier.fillMaxWidth().weight(1f)){Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White));Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=8.dp)){
    Text("▣  Materials",color=Color.White,fontSize=12.sp,modifier=Modifier.fillMaxWidth().background(panel).padding(10.dp))
    project.clips.forEachIndexed{i,uri->Row(Modifier.fillMaxWidth().padding(top=5.dp).clip(RoundedCornerShape(6.dp)).background(if(i==selected)Color(0xFF55515A)else panel).clickable{selected=i;playing=false}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(uri.contains("video",true))Icons.Default.Movie else Icons.Default.Image,null,tint=lime);Text("  Clip "+(i+1),Modifier.weight(1f),color=Color.White,fontSize=12.sp);Icon(Icons.Default.Close,null,tint=muted,modifier=Modifier.clickable{remove(i)})}}
    Text("♫  Add music",color=muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(top=6.dp).background(panel).clickable{import()}.padding(10.dp))
   }}
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(7.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("Video/Photo","Music","Fx","Text","Shapes","3D").forEach{t->Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).background(if(t=="Video/Photo")panel2 else panel).clickable{if(t=="Video/Photo"||t=="Music")import()}.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Icon(when(t){"Music"->Icons.Default.MusicNote;"Fx"->Icons.Default.AutoAwesome;"Text"->Icons.Default.TextFields;"Shapes"->Icons.Default.Category;"3D"->Icons.Default.ViewInAr;else->Icons.Default.VideoLibrary},null,tint=Color.White);Text(t,fontSize=11.sp,color=muted)
   }}
  }
 }
}

@Composable private fun SettingsScreen(accent:Color,setAccent:(Color)->Unit,update:String,download:()->Unit,back:()->Unit){
 Column(Modifier.fillMaxSize().padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ArrowBack,null,Modifier.clickable{back()}.padding(end=12.dp));Text("My",fontSize=22.sp,fontWeight=FontWeight.Bold)}
 Spacer(Modifier.height(28.dp));Text("App theme",fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp))
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf(lime,Color(0xFF7B8CFF),Color(0xFFFF79B8),Color(0xFF57D8C3),Color.White).forEach{Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(it).clickable{setAccent(it)})}}
 Spacer(Modifier.height(28.dp));Text("App updates",fontWeight=FontWeight.Bold);Text(update,color=muted);Button(onClick=download){Text("Open update download")}
 }
}
