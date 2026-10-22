package com.ega.project.vibemovie
import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Satu baris subtitle: tampil pada [start]..[end] (ms, waktu asli berkas, belum ditambah delay). */
data class Cue(val start:Long,val end:Long,val text:String)

/**
 * Subtitle yang sudah dibaca. Delay TIDAK disimpan di sini: [at] menerima waktu video yang sudah dikoreksi,
 * sehingga delay bisa diubah kapan saja tanpa membaca ulang berkas.
 */
class SubTrack(val cues:List<Cue>){
 /** teks semua baris yang aktif pada waktu [t] (ms); null bila tidak ada. [cues] sudah terurut menurut start */
 fun at(t:Long):String?{
  if(cues.isEmpty()||t<0)return null
  // binary search: indeks cue terakhir dengan start <= t
  var lo=0;var hi=cues.size-1;var idx=-1
  while(lo<=hi){
   val m=(lo+hi) ushr 1
   if(cues[m].start<=t){idx=m;lo=m+1} else hi=m-1
  }
  if(idx<0)return null
  // cue sebelumnya bisa masih aktif (tumpang tindih), cek mundur beberapa saja
  var first=idx
  var i=idx
  while(i>=0&&idx-i<8){if(cues[i].end>t)first=i;i--}
  val sb=StringBuilder()
  for(k in first..idx){
   val c=cues[k]
   if(c.end>t&&c.text.isNotEmpty()){if(sb.isNotEmpty())sb.append('\n');sb.append(c.text)}
  }
  return sb.toString().ifEmpty{null}
 }
}

/** Pembaca subtitle lokal: SRT, WebVTT, dan ASS/SSA (teks saja, gaya diabaikan). */
object Subs{
 /** Baca berkas dari [uri] (hasil file picker). null bila gagal dibaca atau tidak ada baris yang dikenali. */
 fun load(c:Context,uri:Uri):SubTrack?=try{
  val bytes=c.contentResolver.openInputStream(uri)?.use{it.readBytes()}
  if(bytes==null||bytes.size>8*1024*1024)null   // subtitle normal < 1 MB; batasi supaya aman
  else parse(decode(bytes)).takeIf{it.cues.isNotEmpty()}
 }catch(e:Exception){null}

 /** Tebak encoding: BOM UTF-8/UTF-16, lalu UTF-8 ketat, lalu Windows-1252 (umum untuk subtitle lama) */
 fun decode(b:ByteArray):String{
  fun s(cs:String,off:Int)=String(b,off,b.size-off,charset(cs))
  if(b.size>=3&&b[0]==0xEF.toByte()&&b[1]==0xBB.toByte()&&b[2]==0xBF.toByte())return s("UTF-8",3)
  if(b.size>=2&&b[0]==0xFF.toByte()&&b[1]==0xFE.toByte())return s("UTF-16LE",2)
  if(b.size>=2&&b[0]==0xFE.toByte()&&b[1]==0xFF.toByte())return s("UTF-16BE",2)
  return try{
   Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(b)).toString()
  }catch(e:Exception){s("windows-1252",0)}
 }

 fun parse(raw:String):SubTrack{
  val t=raw.replace("\r\n","\n").replace('\r','\n').removePrefix("\uFEFF")
  val cues=if(t.contains("[Events]",ignoreCase=true))ass(t) else srtVtt(t)
  return SubTrack(cues.sortedBy{it.start})
 }

 // ---------- SRT / WebVTT ----------
 // jam opsional (VTT boleh "mm:ss.mmm"), pecahan detik dipisah koma atau titik
 private val ts=Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
 private val arrow=Regex("""(\S+)\s*-->\s*(\S+)""")
 private val tagHtml=Regex("""<[^>]*>""")
 private val tagAss=Regex("""\{[^}]*}""")

 private fun ms(s:String):Long?{
  val m=ts.matchEntire(s)?:return null
  val(h,mi,se,f)=m.destructured
  return (h.ifEmpty{"0"}.toLong()*3600+mi.toLong()*60+se.toLong())*1000+(f+"00").take(3).toLong()
 }

 private fun clean(s:String):String =
  s.replace(tagHtml,"").replace(tagAss,"")
   .replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&")
   .lines().map{it.trim()}.filter{it.isNotEmpty()}.joinToString("\n")

 private fun srtVtt(t:String):List<Cue>{
  val out=ArrayList<Cue>()
  for(block in t.split(Regex("""\n[ \t]*\n"""))){
   val lines=block.split('\n')
   val ai=lines.indexOfFirst{it.contains("-->")}
   if(ai<0)continue   // header WEBVTT, NOTE, STYLE, atau nomor yang sendirian
   val m=arrow.find(lines[ai])?:continue
   val a=ms(m.groupValues[1])?:continue
   val b=ms(m.groupValues[2])?:continue
   val txt=clean(lines.drop(ai+1).joinToString("\n"))
   if(txt.isNotEmpty())out+=Cue(a,b,txt)
  }
  return out
 }

 // ---------- ASS / SSA ----------
 private val assTime=Regex("""(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})""")
 private fun assMs(s:String):Long?{
  val m=assTime.matchEntire(s.trim())?:return null
  val(h,mi,se,f)=m.destructured
  val frac=if(f.length==3)f.toLong() else f.toLong()*(if(f.length==1)100 else 10)   // biasanya centisecond ("12" = 120 ms)
  return (h.toLong()*3600+mi.toLong()*60+se.toLong())*1000+frac
 }
 private fun ass(t:String):List<Cue>{
  val out=ArrayList<Cue>()
  var inEvents=false
  var cols=listOf("layer","start","end","style","name","marginl","marginr","marginv","effect","text")
  for(line in t.lines()){
   val l=line.trim()
   if(l.startsWith("[")){inEvents=l.equals("[Events]",ignoreCase=true);continue}
   if(!inEvents)continue
   if(l.startsWith("Format:",ignoreCase=true)){cols=l.substringAfter(':').split(',').map{it.trim().lowercase()};continue}
   if(!l.startsWith("Dialogue:",ignoreCase=true))continue
   val f=l.substringAfter(':').trimStart().split(',',limit=cols.size)   // kolom Text boleh memuat koma
   val si=cols.indexOf("start");val ei=cols.indexOf("end");val ti=cols.indexOf("text")
   if(si<0||ei<0||ti<0||f.size<=maxOf(si,ei,ti))continue
   val a=assMs(f[si])?:continue
   val b=assMs(f[ei])?:continue
   val txt=clean(f[ti].replace("\\N","\n").replace("\\n","\n").replace("\\h"," "))
   if(txt.isNotEmpty())out+=Cue(a,b,txt)
  }
  return out
 }
}
