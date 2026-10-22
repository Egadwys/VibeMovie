@file:OptIn(ExperimentalMaterial3Api::class)
package com.ega.project.vibemovie

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity(){
 /** uri video dari aplikasi lain (intent VIEW); dikonsumsi oleh [Root] */
 val incoming=mutableStateOf<Uri?>(null)
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val t=android.graphics.Color.TRANSPARENT
  enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(t,t),navigationBarStyle=SystemBarStyle.auto(t,t))
  window.isNavigationBarContrastEnforced=false
  if(intent?.action==Intent.ACTION_VIEW)incoming.value=intent.data
  setContent{Root(this)}
 }
 override fun onNewIntent(intent:Intent){
  super.onNewIntent(intent)
  setIntent(intent)
  if(intent.action==Intent.ACTION_VIEW)incoming.value=intent.data
 }
}

@Composable fun MovieTheme(mode:Int,content:@Composable () -> Unit){
 val dark=mode==2||(mode==0&&androidx.compose.foundation.isSystemInDarkTheme())
 val ctx=LocalContext.current
 val darkOv:(ColorScheme)->ColorScheme={it.copy(background=Color.Black,surface=Color.Black,surfaceVariant=Color(0xFF161616),surfaceContainer=Color(0xFF0C0C0C))}
 // Material You (dynamic color) di Android 12+, fallback skema teal di bawahnya
 val cs=when{
  Build.VERSION.SDK_INT>=31->if(dark)darkOv(dynamicDarkColorScheme(ctx)) else dynamicLightColorScheme(ctx)
  dark->darkOv(darkColorScheme(primary=Color(0xFF80CBC4),secondaryContainer=Color(0xFF334B48),onSecondaryContainer=Color(0xFFCCE8E4),tertiary=Color(0xFF9DCAE7)))
  else->lightColorScheme(primary=Color(0xFF00796B),secondaryContainer=Color(0xFFCCE8E4),onSecondaryContainer=Color(0xFF00201D),tertiary=Color(0xFF456179))
 }
 val view=LocalView.current
 SideEffect{
  (view.context as? Activity)?.window?.let{w->
   val ic=WindowCompat.getInsetsController(w,view)
   ic.isAppearanceLightStatusBars=!dark;ic.isAppearanceLightNavigationBars=!dark
  }
 }
 MaterialTheme(cs,content=content)
}

/** Permintaan memutar: daftar putar + indeks awal. [external] = dibuka dari aplikasi lain, back menutup aplikasi. */
class PlayReq(val items:List<Video>,val index:Int,val external:Boolean)

@Composable fun Root(act:MainActivity){
 val c=LocalContext.current
 val sp=c.getSharedPreferences("s",0)
 var mode by remember{mutableIntStateOf(sp.getInt("mode",0))}
 var play by remember{mutableStateOf<PlayReq?>(null)}
 var tick by remember{mutableIntStateOf(0)}   // naik setiap pemutar ditutup, supaya penanda "lanjut menonton" di daftar diperbarui
 val inc=act.incoming.value
 LaunchedEffect(inc){
  if(inc!=null){play=PlayReq(listOf(videoFromUri(c,inc)),0,true);act.incoming.value=null}
 }
 MovieTheme(mode){
  Surface(Modifier.fillMaxSize()){
   // Library tetap tersusun di bawah pemutar supaya daftar dan posisi scroll tidak hilang
   Library(mode,{m->mode=m;sp.edit().putInt("mode",m).apply()},tick){items,i->play=PlayReq(items,i,false)}
   val p=play
   if(p!=null)PlayerScreen(p.items,p.index){
    play=null;tick++
    if(p.external)act.finish()
   }
  }
 }
}

private fun hasPerm(c:android.content.Context)=ContextCompat.checkSelfPermission(c,
 if(Build.VERSION.SDK_INT>=33)android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED

@Composable fun Library(mode:Int,setMode:(Int)->Unit,tick:Int,onPlay:(List<Video>,Int)->Unit){
 val c=LocalContext.current
 val sp=c.getSharedPreferences("s",0)
 var granted by remember{mutableStateOf(hasPerm(c))}
 var videos by remember{mutableStateOf<List<Video>?>(null)}
 var folderVer by remember{mutableIntStateOf(0)}
 var query by remember{mutableStateOf<String?>(null)}   // null = kolom cari tertutup
 var folder by remember{mutableStateOf<String?>(null)}
 var sort by remember{mutableIntStateOf(sp.getInt("sort",0))}
 var settings by remember{mutableStateOf(false)}
 var sortMenu by remember{mutableStateOf(false)}
 val permReq=if(Build.VERSION.SDK_INT>=33)android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE
 val permLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
 val openFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->
  if(u!=null){
   runCatching{c.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
   onPlay(listOf(videoFromUri(c,u)),0)
  }
 }
 LaunchedEffect(Unit){if(!granted)permLauncher.launch(permReq)}
 LaunchedEffect(granted,folderVer){
  if(granted)videos=withContext(Dispatchers.IO){runCatching{loadVideos(c)}.getOrDefault(emptyList())}
 }
 val all=videos
 val folderNames=remember(all){all.orEmpty().map{it.folder}.distinct().sorted()}
 val shown=remember(all,folder,query,sort){
  val q=query?.trim().orEmpty()
  all.orEmpty().filter{(folder==null||it.folder==folder)&&(q.isEmpty()||it.title.contains(q,true))}.let{l->
   when(sort){0->l.sortedByDescending{it.added};2->l.sortedByDescending{it.dur};else->l.sortedBy{it.title.lowercase()}}
  }
 }

 Scaffold(topBar={
  TopAppBar(
   title={
    val q=query
    if(q==null)Text("VibeMovie",fontWeight=FontWeight.Bold)
    else{
     val fr=remember{FocusRequester()}
     TextField(q,{query=it},singleLine=true,placeholder={Text("Cari video")},
      modifier=Modifier.fillMaxWidth().focusRequester(fr),
      colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
       focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
     LaunchedEffect(Unit){fr.requestFocus()}
    }
   },
   actions={
    if(query!=null)IconButton({query=null}){Icon(Icons.Default.Close,"Tutup pencarian")}
    else{
     IconButton({query=""}){Icon(Icons.Default.Search,"Cari")}
     Box{
      IconButton({sortMenu=true}){Icon(Icons.Default.Sort,"Urutkan")}
      DropdownMenu(sortMenu,{sortMenu=false}){
       listOf("Terbaru","Nama","Durasi").forEachIndexed{i,n->
        DropdownMenuItem(text={Text(n)},onClick={sort=i;sp.edit().putInt("sort",i).apply();sortMenu=false},
         trailingIcon={if(sort==i)Icon(Icons.Default.Check,null)})
       }
      }
     }
     IconButton({openFile.launch(arrayOf("video/*"))}){Icon(Icons.Default.FolderOpen,"Buka berkas video")}
     IconButton({settings=true}){Icon(Icons.Default.Settings,"Pengaturan")}
    }
   })
 }){pad->
  Column(Modifier.padding(pad).fillMaxSize()){
   when{
    !granted->Empty("Izin akses video diperlukan untuk menampilkan daftar video.","Beri izin"){permLauncher.launch(permReq)}
    all==null->Box(Modifier.fillMaxSize(),Alignment.Center){CircularProgressIndicator()}
    all.isEmpty()->Empty("Tidak ada video ditemukan.\nCek folder sumber di Pengaturan, atau buka berkas langsung.","Buka berkas video"){openFile.launch(arrayOf("video/*"))}
    else->{
     if(folderNames.size>1)LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=4.dp)){
      item{FilterChip(folder==null,{folder=null},{Text("Semua")})}
      items(folderNames){f->FilterChip(folder==f,{folder=if(folder==f)null else f},{Text(f)})}
     }
     if(shown.isEmpty())Box(Modifier.fillMaxSize(),Alignment.Center){Text("Tidak ada hasil",color=MaterialTheme.colorScheme.onSurfaceVariant)}
     else LazyColumn(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      items(shown,key={it.key}){v->VideoRow(v,tick){onPlay(shown,shown.indexOf(v))}}
     }
    }
   }
  }
 }
 if(settings)SettingsDialog(mode,setMode,{folderVer++},{settings=false})
}

@Composable private fun Empty(msg:String,btn:String,act:()->Unit){
 Column(Modifier.fillMaxSize().padding(32.dp),Arrangement.Center,Alignment.CenterHorizontally){
  Icon(Icons.Default.VideoLibrary,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  Spacer(Modifier.height(16.dp))
  Text(msg,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Spacer(Modifier.height(16.dp))
  Button(act){Text(btn)}
 }
}

@Composable private fun VideoRow(v:Video,tick:Int,onClick:()->Unit){
 val c=LocalContext.current
 val bmp by produceState(Thumbs.peek(v),v.key){if(value==null)value=Thumbs.get(c,v)}
 val resume=remember(v.key,tick){Store.pos(c,v.key)}
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.width(136.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)){
   bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
   if(bmp==null)Icon(Icons.Default.PlayArrow,null,Modifier.align(Alignment.Center),tint=MaterialTheme.colorScheme.onSurfaceVariant)
   Text(fmtTime(v.dur),Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=0.7f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp),
    color=Color.White,fontSize=11.sp)
   if(resume>0&&v.dur>0)LinearProgressIndicator(progress={(resume.toFloat()/v.dur).coerceIn(0f,1f)},Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
    trackColor=Color.Black.copy(alpha=0.4f))
  }
  Spacer(Modifier.width(12.dp))
  Column(Modifier.weight(1f)){
   Text(v.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
   val res=if(v.h>0)"${v.w}×${v.h} · " else ""
   Text("${v.folder} · $res${fmtSize(v.size)}",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}

@Composable private fun SettingsDialog(mode:Int,setMode:(Int)->Unit,onFoldersChanged:()->Unit,close:()->Unit){
 val c=LocalContext.current
 var folders by remember{mutableStateOf(Folders.get(c))}
 val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->
  u?.let{Folders.fromTree(it)}?.let{Folders.add(c,it);folders=Folders.get(c);onFoldersChanged()}
 }
 AlertDialog(onDismissRequest=close,confirmButton={TextButton(close){Text("Selesai")}},title={Text("Pengaturan")},text={
  Column{
   Text("Tema",fontWeight=FontWeight.Medium)
   listOf("Ikuti sistem","Terang","Gelap AMOLED").forEachIndexed{i,n->
    Row(Modifier.fillMaxWidth().clickable{setMode(i)},verticalAlignment=Alignment.CenterVertically){
     RadioButton(mode==i,{setMode(i)});Text(n)
    }
   }
   Spacer(Modifier.height(12.dp))
   Text("Folder sumber video",fontWeight=FontWeight.Medium)
   if(folders.isEmpty())Text("Semua folder dipindai.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
   folders.forEach{f->
    Row(verticalAlignment=Alignment.CenterVertically){
     Text(Folders.label(f),Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
     IconButton({Folders.remove(c,f);folders=Folders.get(c);onFoldersChanged()}){Icon(Icons.Default.Close,"Hapus folder")}
    }
   }
   TextButton({pick.launch(null)}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Tambah folder")}
  }
 })
}
