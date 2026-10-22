package com.ega.project.vibemovie.data.remote

import com.ega.project.vibemovie.data.model.SubtitleItem
import retrofit2.http.GET
import retrofit2.http.Query

interface SubtitleApiService {
    @GET("subtitles/search")
    suspend fun searchSubtitles(
        @Query("query") title: String,
        @Query("languages") languages: String = "id,en"
    ): List<SubtitleResponseDto>
}

data class SubtitleResponseDto(
    val id: String,
    val language: String,
    val languageName: String,
    val downloadUrl: String
) {
    fun toSubtitleItem(): SubtitleItem {
        return SubtitleItem(
            id = id,
            language = language,
            languageName = if (language == "id") "Indonesian (Bahasa Indonesia)" else "English",
            sourceName = "Cloud (OpenSubtitles)",
            isCloud = true
        )
    }
}
