package com.ega.project.vibemovie.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.ega.project.vibemovie.data.model.VideoItem

class LocalVideoScraper(private val context: Context) {

    fun getLocalVideos(): List<VideoItem> {
        val videoList = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.HEIGHT
        )

        val query = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Video"
                val path = cursor.getString(pathColumn) ?: ""
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val height = cursor.getInt(heightColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val res = if (height >= 2160) "4K UHD" else if (height >= 1080) "1080p FHD" else "720p HD"

                videoList.add(
                    VideoItem(
                        id = id,
                        title = name,
                        path = path,
                        uri = contentUri,
                        duration = duration,
                        size = size,
                        resolution = res,
                        isHdr = height >= 2160,
                        codec = "H.265 / HEVC"
                    )
                )
            }
        }
        return videoList
    }
}
