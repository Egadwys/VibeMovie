@file:OptIn(UnstableApi::class)
package com.ega.project.vibemovie

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private tailrec fun Context.findActivity():Activity? = when(this){is Activity->this;is ContextWrapper->baseContext.findActivity();else->null}

/** File picker subtitle yang langsung terbuka di folder video (bila diketahui) */
private class PickDoc(val initial:()->Uri?):ActivityResultContracts.OpenDocument(){
 override fun createIntent(context:Context,input:Array<String>):Intent =
  super.createIntent(context,input).also{i->initial()?.let{i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,it)}}
}

private val SPEEDS=floatArrayOf(0.5f,0.75f,1f,1.25f,1.5f,2f)
private val RESIZE=intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL)
private val RESIZE_NAME=arrayOf("Sesuai layar","Zoom","Penuh")
private const val SEEK_STEP=10_000L

/**
 * Layar pemutar video penuh. Subtitle digambar sendiri (bukan lewat ExoPlayer) supaya delay bisa diubah
 * langsung saat menonton: teks yang tampil = baris subtitle pada waktu (posisi video - delay).
 */
@Composable fun PlayerScreen(items:List<Video>,start:Int,onClose:()->Unit){
 val c=LocalContext.current
 val act=remember{c.findActivity()}
 val view=LocalView.current
 val scope=rememberCoroutineScope()
 val cfg=LocalConfiguration.current

 // ---- player
 val exo=remember{
  ExoPlayer.Builder(c).build().apply{
   setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
   setHandleAudioBecomingNoisy(true)
   // subtitle bawaan video tidak dipakai; yang tampil hanya subtitle lokal pilihan pengguna
   trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
   setMediaItems(items.map{MediaItem.Builder().setUri(it.uri).setMediaId(it.key).build()},start,Store.pos(c,items[start].key))
   prepare();playWhenReady=true
  }
 }

 // ---- state
 val pos=remember{mutableLongStateOf(0L)}
 val dur=remember{mutableLongStateOf(0L)}
 var cur by remember{mutableIntStateOf(start)}
 var pw by remember{mutableStateOf(true)}            // playWhenReady
 var ui by remember{mutableStateOf(true)}            // kontrol tampil
 var uiTick by remember{mutableIntStateOf(0)}        // naik tiap interaksi, mereset timer sembunyi otomatis
 var locked by remember{mutableStateOf(false)}
 var panel by remember{mutableStateOf(false)}        // panel subtitle
 var speed by remember{mutableFloatStateOf(1f)}
 var resize by remember{mutableIntStateOf(0)}
 var hud by remember{mutableStateOf<String?>(null)}
 var err by remember{mutableStateOf<String?>(null)}
 var audio by remember{mutableStateOf<List<Tracks.Group>>(emptyList())}
 var speedMenu by remember{mutableStateOf(false)}
 var audioMenu by remember{mutableStateOf(false)}
 // subtitle
 var track by remember{mutableStateOf<SubTrack?>(null)}
 var subName by remember{mutableStateOf<String?>(null)}
 var offset by remember{mutableLongStateOf(0L)}      // delay subtitle (ms); + = muncul lebih lambat
 var subSize by remember{mutableIntStateOf(Store.subSize(c))}
 val video=items[cur]

 fun poke(){uiTick++}
 fun toast(s:String)=Toast.makeText(c,s,Toast.LENGTH_SHORT).show()
 fun saveNow(){
  val i=exo.currentMediaItemIndex
  items.getOrNull(i)?.let{Store.savePos(c,it.key,exo.currentPosition,exo.duration.coerceAtLeast(0L))}
 }
 fun seekBy(d:Long){
  val t=exo.currentPosition+d
  val end=exo.duration
  exo.seekTo(if(end>0)t.coerceIn(0L,end) else t.coerceAtLeast(0L))
  hud=(if(d>0)"+" else "−")+"${Math.abs(d)/1000} dtk"
 }
 fun togglePlay(){
  if(exo.playWhenReady)exo.pause()
  else{if(exo.playbackState==Player.STATE_ENDED)exo.seekTo(0L);exo.play()}
 }
 fun setOffset(v:Long){offset=v.coerceIn(-60_000L,60_000L);Store.setDelay(c,items[cur].key,offset)}

 // ---- listener ExoPlayer
 DisposableEffect(exo){
  val l=object:Player.Listener{
   override fun onPlayWhenReadyChanged(p:Boolean,reason:Int){pw=p}
   override fun onMediaItemTransition(m:MediaItem?,reason:Int){cur=exo.currentMediaItemIndex.coerceIn(0,items.size-1)}
   override fun onPositionDiscontinuity(old:Player.PositionInfo,nw:Player.PositionInfo,reason:Int){
    // berpindah video: simpan posisi video lama (atau hapus bila selesai ditonton)
    if(old.mediaItemIndex!=nw.mediaItemIndex){
     val ov=items.getOrNull(old.mediaItemIndex)?:return
     if(reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION)Store.clearPos(c,ov.key) else Store.savePos(c,ov.key,old.positionMs,ov.dur)
    }
   }
   override fun onPlaybackStateChanged(s:Int){
    if(s==Player.STATE_ENDED){ui=true;items.getOrNull(exo.currentMediaItemIndex)?.let{Store.clearPos(c,it.key)}}
   }
   override fun onTracksChanged(t:Tracks){
    audio=t.groups.filter{it.type==C.TRACK_TYPE_AUDIO&&it.isSupported}
   }
   override fun onVideoSizeChanged(vs:VideoSize){
    // video lebar -> landscape, video vertikal -> portrait (tetap bisa diputar manual lewat tombol rotasi)
    if(vs.width>0&&vs.height>0)act?.requestedOrientation=
     if(vs.width>=vs.height)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
   }
   override fun onPlayerError(e:PlaybackException){err="${e.errorCodeName}\n${e.message?:""}"}
  }
  exo.addListener(l)
  onDispose{exo.removeListener(l)}
 }

 // ---- layar penuh, layar menyala, bersihkan saat keluar
 DisposableEffect(Unit){
  val w=act?.window
  val ic=w?.let{WindowCompat.getInsetsController(it,view)}
  ic?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  ic?.hide(WindowInsetsCompat.Type.systemBars())
  w?.let{it.attributes=it.attributes.also{lp->lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}}
  onDispose{
   saveNow()
   exo.release()
   ic?.show(WindowInsetsCompat.Type.systemBars())
   w?.let{it.attributes=it.attributes.also{lp->
    lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    lp.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
   }}
   act?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  }
 }
 DisposableEffect(pw){view.keepScreenOn=pw;onDispose{view.keepScreenOn=false}}

 // berhenti saat aplikasi ke latar belakang
 val owner=LocalLifecycleOwner.current
 DisposableEffect(owner){
  val o=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_STOP){exo.pause();saveNow()}}
  owner.lifecycle.addObserver(o)
  onDispose{owner.lifecycle.removeObserver(o)}
 }

 // ---- polling posisi (100 ms: cukup halus untuk subtitle) + simpan posisi berkala
 LaunchedEffect(exo){
  var saved=0L
  while(true){
   pos.longValue=exo.currentPosition
   dur.longValue=exo.duration.coerceAtLeast(0L)
   if(exo.isPlaying){
    val now=android.os.SystemClock.uptimeMillis()
    if(now-saved>5000){saved=now;saveNow()}
   }
   delay(100)
  }
 }
 // sembunyikan kontrol otomatis saat sedang memutar
 LaunchedEffect(ui,uiTick,pw,panel){if(ui&&pw&&!panel){delay(3500);ui=false}}
 LaunchedEffect(hud){if(hud!=null){delay(900);hud=null}}

 // ---- subtitle: muat subtitle & delay tersimpan tiap ganti video
 val key=video.key
 LaunchedEffect(key){
  track=null;subName=null;offset=Store.delay(c,key)
  val u=Store.subUri(c,key)
  if(u!=null){
   val t=withContext(Dispatchers.IO){Subs.load(c,Uri.parse(u))}
   if(t!=null){track=t;subName=Store.subName(c,key)}
   else{Store.setSub(c,key,null,null);toast("Subtitle tersimpan tidak bisa dibuka lagi, muat ulang")}
  }
 }
 val pick=rememberLauncherForActivityResult(PickDoc{
  items[cur].rel?.let{DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:"+it.trim('/'))}
 }){u->
  if(u!=null)scope.launch{
   val t=withContext(Dispatchers.IO){Subs.load(c,u)}
   if(t==null)toast("Subtitle tidak bisa dibaca. Format yang didukung: SRT, VTT, ASS/SSA")
   else{
    runCatching{c.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    val n=displayName(c,u)
    track=t;subName=n
    Store.setSub(c,items[cur].key,u.toString(),n)
    toast("Subtitle dimuat (${t.cues.size} baris)")
   }
  }
 }

 // ---- kecerahan & volume (gestur geser vertikal)
 val am=remember{c.getSystemService(Context.AUDIO_SERVICE) as AudioManager}
 fun vol01():Float=am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
 fun setVol(v:Float){am.setStreamVolume(AudioManager.STREAM_MUSIC,(v*am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(),0)}
 fun bright01():Float{
  val b=act?.window?.attributes?.screenBrightness?:-1f
  return if(b>=0f)b else try{Settings.System.getInt(c.contentResolver,Settings.System.SCREEN_BRIGHTNESS)/255f}catch(e:Exception){0.5f}
 }
 fun setBright(v:Float){act?.window?.let{w->w.attributes=w.attributes.also{lp->lp.screenBrightness=v.coerceIn(0.02f,1f)}}}

 BackHandler(true){if(panel)panel=false else onClose()}

 MaterialTheme(darkColorScheme(primary=Color(0xFF80CBC4),secondaryContainer=Color(0xFF334B48),onSecondaryContainer=Color(0xFFCCE8E4))){
 Surface(Modifier.fillMaxSize(),color=Color.Black,contentColor=Color.White){
  Box(Modifier.fillMaxSize()){
   // 1) permukaan video
   AndroidView(factory={ctx->
    PlayerView(ctx).apply{
     useController=false
     player=exo
     subtitleView?.visibility=View.GONE
    }
   },update={it.resizeMode=RESIZE[resize]},modifier=Modifier.fillMaxSize())

   // 2) gestur: ketuk = tampil/sembunyi kontrol, ketuk 2x = ±10 dtk / play-pause, geser vertikal = kecerahan (kiri) / volume (kanan)
   Box(Modifier.fillMaxSize()
    .pointerInput(locked){
     detectTapGestures(
      onTap={ui=!ui;poke()},
      onDoubleTap={o->
       if(!locked){
        val w=size.width
        when{o.x<w/3f->seekBy(-SEEK_STEP);o.x>w*2f/3f->seekBy(SEEK_STEP);else->togglePlay()}
       }
      })
    }
    .pointerInput(locked){
     if(locked)return@pointerInput
     var left=false;var base=0f;var acc=0f
     detectVerticalDragGestures(
      onDragStart={o->left=o.x<size.width/2f;acc=0f;base=if(left)bright01() else vol01()},
      onDragEnd={},onDragCancel={}
     ){ch,dy->
      ch.consume()
      acc-=dy/size.height*1.3f
      val v=(base+acc).coerceIn(0f,1f)
      if(left){setBright(v);hud="Kecerahan ${(v*100).roundToInt()}%"}
      else{setVol(v);hud="Volume ${(v*100).roundToInt()}%"}
     }
    })

   // 3) subtitle
   SubtitleOverlay(track,pos,offset,subSize,if(ui&&!locked)104.dp else 28.dp,Modifier.align(Alignment.BottomCenter))

   // 4) HUD
   hud?.let{
    Text(it,Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=0.65f),RoundedCornerShape(12.dp)).padding(horizontal=18.dp,vertical=10.dp),
     color=Color.White,fontWeight=FontWeight.Medium)
   }

   // 5) kontrol
   AnimatedVisibility(ui&&!locked,enter=fadeIn(),exit=fadeOut()){
    Box(Modifier.fillMaxSize().safeDrawingPadding()){
     // atas
     Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.7f),Color.Transparent))).padding(horizontal=4.dp),
      verticalAlignment=Alignment.CenterVertically){
      IconButton({onClose()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Kembali")}
      Text(video.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
      if(audio.size>1)Box{
       IconButton({audioMenu=true;poke()}){Icon(Icons.Default.Audiotrack,"Trek audio")}
       DropdownMenu(audioMenu,{audioMenu=false}){
        audio.forEachIndexed{i,g->
         val f=g.getTrackFormat(0)
         val n=f.label?:f.language?.let{java.util.Locale.forLanguageTag(it).displayLanguage}?.takeIf{it.isNotBlank()}?:"Audio ${i+1}"
         DropdownMenuItem(text={Text(n)},trailingIcon={if(g.isSelected)Icon(Icons.Default.Check,null)},onClick={
          exo.trackSelectionParameters=exo.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,0)).build()
          audioMenu=false
         })
        }
       }
      }
      IconButton({panel=!panel;poke()}){
       Icon(if(track!=null)Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,"Subtitle",
        tint=if(track!=null||panel)MaterialTheme.colorScheme.primary else Color.White)
      }
      IconButton({resize=(resize+1)%RESIZE.size;hud=RESIZE_NAME[resize];poke()}){Icon(Icons.Default.AspectRatio,"Rasio layar")}
      IconButton({
       act?.requestedOrientation=if(cfg.orientation==Configuration.ORIENTATION_LANDSCAPE)ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
       poke()
      }){Icon(Icons.Default.ScreenRotation,"Putar layar")}
     }
     // tengah
     Row(Modifier.align(Alignment.Center),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
      IconButton({exo.seekToPreviousMediaItem();poke()},enabled=cur>0){Icon(Icons.Default.SkipPrevious,"Sebelumnya",Modifier.size(32.dp))}
      IconButton({seekBy(-SEEK_STEP);poke()}){Icon(Icons.Default.Replay10,"Mundur 10 detik",Modifier.size(36.dp))}
      FilledIconButton({togglePlay();poke()},Modifier.size(68.dp)){
       Icon(if(pw)Icons.Default.Pause else Icons.Default.PlayArrow,if(pw)"Jeda" else "Putar",Modifier.size(40.dp))
      }
      IconButton({seekBy(SEEK_STEP);poke()}){Icon(Icons.Default.Forward10,"Maju 10 detik",Modifier.size(36.dp))}
      IconButton({exo.seekToNextMediaItem();poke()},enabled=cur<items.size-1){Icon(Icons.Default.SkipNext,"Berikutnya",Modifier.size(32.dp))}
     }
     // bawah
     Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=0.7f)))).padding(horizontal=12.dp)){
      SeekBar(pos,dur,{exo.seekTo(it);poke()},{poke()})
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
       IconButton({locked=true;ui=false}){Icon(Icons.Default.LockOpen,"Kunci layar")}
       Spacer(Modifier.weight(1f))
       Box{
        TextButton({speedMenu=true;poke()}){Icon(Icons.Default.Speed,null);Spacer(Modifier.width(6.dp));Text(if(speed==1f)"1x" else "${speed}x",color=Color.White)}
        DropdownMenu(speedMenu,{speedMenu=false}){
         SPEEDS.forEach{s->
          DropdownMenuItem(text={Text(if(s==1f)"Normal (1x)" else "${s}x")},trailingIcon={if(speed==s)Icon(Icons.Default.Check,null)},
           onClick={speed=s;exo.setPlaybackSpeed(s);speedMenu=false})
         }
        }
       }
      }
     }
    }
   }

   // 6) tombol buka kunci
   AnimatedVisibility(ui&&locked,Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start=12.dp),enter=fadeIn(),exit=fadeOut()){
    FilledTonalIconButton({locked=false;ui=true;poke()}){Icon(Icons.Default.Lock,"Buka kunci layar")}
   }

   // 7) panel subtitle: tidak modal, supaya subtitle tetap terlihat saat delay disetel
   if(panel)SubPanel(subName,track!=null,offset,subSize,
    onLoad={pick.launch(arrayOf("*/*"))},
    onOff={track=null;subName=null;Store.setSub(c,items[cur].key,null,null)},
    onOffset={setOffset(it)},
    onSize={subSize=it.coerceIn(12,44);Store.setSubSize(c,subSize)},
    onClose={panel=false},
    Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp))
  }
 }

 err?.let{e->
  AlertDialog(onDismissRequest={},title={Text("Gagal memutar video")},text={Text(e)},
   confirmButton={TextButton({err=null;onClose()}){Text("Kembali")}})
 }
 }
}

@Composable private fun SubtitleOverlay(track:SubTrack?,pos:LongState,offset:Long,size:Int,bottom:Dp,mod:Modifier){
 // derivedStateOf: hanya berubah (dan menyusun ulang) saat teksnya berganti, bukan tiap 100 ms
 val text by remember(track,offset){derivedStateOf{track?.at(pos.longValue-offset)}}
 text?.let{
  Box(mod.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=bottom),contentAlignment=Alignment.BottomCenter){
   Text(it,Modifier.background(Color.Black.copy(alpha=0.45f),RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=2.dp),
    color=Color.White,fontSize=size.sp,lineHeight=(size*1.25f).sp,textAlign=TextAlign.Center,fontWeight=FontWeight.Medium,
    style=TextStyle(shadow=Shadow(Color.Black,Offset(1.5f,1.5f),5f)))
  }
 }
}

@Composable private fun SeekBar(pos:LongState,dur:LongState,onSeek:(Long)->Unit,onTouch:()->Unit){
 var scrub by remember{mutableStateOf<Float?>(null)}
 val d=dur.longValue.coerceAtLeast(1L).toFloat()
 val v=(scrub?:pos.longValue.toFloat()).coerceIn(0f,d)
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  Text(fmtTime(v.toLong()),fontSize=12.sp)
  Slider(v,{scrub=it;onTouch()},Modifier.weight(1f).padding(horizontal=8.dp),valueRange=0f..d,
   onValueChangeFinished={scrub?.let{s->onSeek(s.toLong())};scrub=null})
  Text(fmtTime(dur.longValue),fontSize=12.sp)
 }
}

@Composable private fun SubPanel(name:String?,hasTrack:Boolean,offset:Long,size:Int,
 onLoad:()->Unit,onOff:()->Unit,onOffset:(Long)->Unit,onSize:(Int)->Unit,onClose:()->Unit,mod:Modifier){
 Surface(mod.widthIn(max=340.dp),shape=RoundedCornerShape(16.dp),color=Color(0xE6161616),contentColor=Color.White){
  Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("Subtitle",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=16.sp)
    IconButton(onClose,Modifier.size(32.dp)){Icon(Icons.Default.Close,"Tutup panel")}
   }
   Text(name?:"Belum ada subtitle dimuat",maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=13.sp,color=Color.White.copy(alpha=0.7f))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(onLoad){Icon(Icons.Default.FolderOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Muat subtitle")}
    TextButton(onOff,enabled=hasTrack){Text("Matikan")}
   }
   HorizontalDivider(color=Color.White.copy(alpha=0.15f))
   Text("Delay subtitle",fontWeight=FontWeight.Medium)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
    DelayBtn("−1s"){onOffset(offset-1000)}
    DelayBtn("−0,1"){onOffset(offset-100)}
    Text(fmtDelay(offset),Modifier.weight(1f),textAlign=TextAlign.Center,fontWeight=FontWeight.Bold,fontSize=16.sp,
     color=if(offset==0L)Color.White else MaterialTheme.colorScheme.primary)
    DelayBtn("+0,1"){onOffset(offset+100)}
    DelayBtn("+1s"){onOffset(offset+1000)}
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    TextButton({onOffset(offset-500)}){Text("−0,5s")}
    TextButton({onOffset(0)},enabled=offset!=0L){Text("Reset")}
    TextButton({onOffset(offset+500)}){Text("+0,5s")}
   }
   Text("+ = subtitle muncul lebih lambat\n− = subtitle muncul lebih cepat",fontSize=11.sp,color=Color.White.copy(alpha=0.55f))
   HorizontalDivider(color=Color.White.copy(alpha=0.15f))
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("Ukuran teks",Modifier.weight(1f),fontWeight=FontWeight.Medium)
    DelayBtn("A−"){onSize(size-2)}
    Text("$size",Modifier.width(40.dp),textAlign=TextAlign.Center)
    DelayBtn("A+"){onSize(size+2)}
   }
  }
 }
}

@Composable private fun DelayBtn(label:String,onClick:()->Unit){
 FilledTonalButton(onClick,contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.defaultMinSize(minWidth=1.dp,minHeight=36.dp)){Text(label,fontSize=13.sp)}
}
