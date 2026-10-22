package com.ega.project.vibemovie
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore.Video.Media as M
import android.provider.OpenableColumns
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * [key] = kunci penyimpanan (posisi terakhir, subtitle, delay): "v<id>" untuk video MediaStore, alamat uri untuk video dari luar.
 * [rel] = path relatif di penyimpanan (mis. "Movies/Anime/"), dipakai agar file picker subtitle langsung membuka folder video.
 */
data class Video(val key:String,val uri:Uri,val title:String,val dur:Long,val size:Long,val w:Int,val h:Int,
 val folder:String,val rel:String?,val added:Long)

fun fmtTime(ms:Long):String{
 val s=(ms.coerceAtLeast(0)/1000).toInt()
 val h=s/3600;val m=s%3600/60;val x=s%60
 return if(h>0)"%d:%02d:%02d".format(h,m,x) else "%02d:%02d".format(m,x)
}
fun fmtSize(b:Long):String=when{
 b>=1L shl 30->"%.1f GB".format(b/1073741824.0)
 b>=1L shl 20->"%.0f MB".format(b/1048576.0)
 else->"%.0f KB".format(b/1024.0)
}
/** delay subtitle untuk tampilan: "+0,5 dtk" / "-1,2 dtk" / "0 dtk" */
fun fmtDelay(ms:Long):String{
 if(ms==0L)return "0,0 dtk"
 return (if(ms>0)"+" else "-")+"%d,%d dtk".format(Math.abs(ms)/1000,Math.abs(ms)%1000/100)
}

/**
 * Folder sumber video pilihan pengguna. Disimpan sebagai id dokumen SAF (mis. "primary:Movies/Anime").
 * Kosong = semua folder dipindai. Hanya path-nya yang dipakai, dicocokkan ke kolom RELATIVE_PATH MediaStore.
 */
object Folders{
 private const val K="video_folders"
 private fun sp(c:Context)=c.getSharedPreferences("s",0)
 fun get(c:Context):List<String> = (sp(c).getStringSet(K,emptySet())?:emptySet()).toList().sorted()
 private fun put(c:Context,l:Collection<String>){sp(c).edit().putStringSet(K,l.toSet()).apply()}
 fun add(c:Context,id:String){put(c,get(c)+id)}
 fun remove(c:Context,id:String){put(c,get(c)-id)}
 fun fromTree(uri:Uri):String?=runCatching{DocumentsContract.getTreeDocumentId(uri)}.getOrNull()
 fun path(id:String)=id.substringAfter(':',"").trim('/')
 fun label(id:String):String{
  val p=path(id)
  return (if(p.isEmpty())"Seluruh penyimpanan" else p)+(if(id.substringBefore(':')!="primary")" (kartu SD)" else "")
 }
}

/** Memuat video dari MediaStore; bila pengguna memilih folder, hanya video di dalam folder tersebut (termasuk subfolder) */
fun loadVideos(c:Context):List<Video>{
 val out=ArrayList<Video>()
 val sel=Folders.get(c).map{Folders.path(it).lowercase()}.filter{it.isNotEmpty()}.distinct()
 c.contentResolver.query(M.EXTERNAL_CONTENT_URI,
  arrayOf(M._ID,M.DISPLAY_NAME,M.DURATION,M.SIZE,M.WIDTH,M.HEIGHT,M.RELATIVE_PATH,M.DATE_ADDED),
  null,null,"${M.DISPLAY_NAME} COLLATE NOCASE")?.use{cu->
  while(cu.moveToNext()){
   val rel=cu.getString(6)?:""
   val parts=rel.split("/").filter{it.isNotBlank()}
   if(sel.isNotEmpty()){
    val low=parts.joinToString("/").lowercase()
    if(sel.none{low==it||low.startsWith("$it/")})continue
   }
   val id=cu.getLong(0)
   out+=Video("v$id",ContentUris.withAppendedId(M.EXTERNAL_CONTENT_URI,id),
    (cu.getString(1)?:"Video").substringBeforeLast('.'),cu.getLong(2),cu.getLong(3),cu.getInt(4),cu.getInt(5),
    parts.lastOrNull()?:"Penyimpanan",rel.ifBlank{null},cu.getLong(7))
  }
 }
 return out
}

/** Video yang dibuka dari luar (intent VIEW / tombol "Buka berkas"): tidak ada metadata MediaStore, nama diambil dari penyedia berkas */
fun videoFromUri(c:Context,uri:Uri):Video{
 var name:String?=null;var size=0L
 runCatching{
  c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{
   if(it.moveToFirst()){name=it.getString(0);size=it.getLong(1)}
  }
 }
 val t=(name?:uri.lastPathSegment?:"Video").substringBeforeLast('.')
 return Video(uri.toString(),uri,t,0L,size,0,0,"",null,0L)
}

/** Pengaturan dan memori per video: posisi terakhir, subtitle yang dipakai, delay subtitle. Semuanya lokal di perangkat. */
object Store{
 private fun sp(c:Context)=c.getSharedPreferences("movie",0)
 fun pos(c:Context,k:String)=sp(c).getLong("p_$k",0L)
 fun setPos(c:Context,k:String,v:Long){sp(c).edit().putLong("p_$k",v).apply()}
 fun clearPos(c:Context,k:String){sp(c).edit().remove("p_$k").apply()}
 /** simpan posisi, atau hapus bila baru mulai / hampir selesai (pakai [dur] <= 0 bila durasi tidak diketahui) */
 fun savePos(c:Context,k:String,v:Long,dur:Long){
  if(v<5000||(dur>0&&v>dur-8000))clearPos(c,k) else setPos(c,k,v)
 }
 fun subUri(c:Context,k:String):String?=sp(c).getString("s_$k",null)
 fun subName(c:Context,k:String):String?=sp(c).getString("sn_$k",null)
 fun setSub(c:Context,k:String,uri:String?,name:String?){
  val e=sp(c).edit()
  if(uri==null)e.remove("s_$k").remove("sn_$k") else e.putString("s_$k",uri).putString("sn_$k",name)
  e.apply()
 }
 fun delay(c:Context,k:String)=sp(c).getLong("d_$k",0L)
 fun setDelay(c:Context,k:String,v:Long){sp(c).edit().apply{if(v==0L)remove("d_$k") else putLong("d_$k",v)}.apply()}
 /** ukuran teks subtitle (sp), berlaku untuk semua video */
 fun subSize(c:Context)=sp(c).getInt("sub_size",22)
 fun setSubSize(c:Context,v:Int){sp(c).edit().putInt("sub_size",v).apply()}
}

/** Nama berkas dari uri hasil file picker, untuk ditampilkan di panel subtitle */
fun displayName(c:Context,uri:Uri):String{
 var n:String?=null
 runCatching{
  c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)}
 }
 return n?:uri.lastPathSegment?:"subtitle"
}

/** Thumbnail video (frame dari sistem), di-cache di memori. */
object Thumbs{
 private val cache=object:android.util.LruCache<String,Bitmap>(24*1024*1024){
  override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
 }
 private val none=java.util.Collections.synchronizedSet(HashSet<String>())
 private val sem=Semaphore(3)
 fun peek(v:Video):Bitmap?=cache.get(v.key)
 suspend fun get(c:Context,v:Video):Bitmap?{
  cache.get(v.key)?.let{return it}
  if(v.key in none)return null
  return sem.withPermit{withContext(Dispatchers.IO){
   try{c.contentResolver.loadThumbnail(v.uri,Size(384,216),null).also{cache.put(v.key,it)}}
   catch(e:Exception){none.add(v.key);null}
  }}
 }
}
