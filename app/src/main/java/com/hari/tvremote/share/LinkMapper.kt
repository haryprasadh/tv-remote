package com.hari.tvremote.share

import java.net.URI

/**
 * Turns shared text into a TV app link, or null if the TV can't open it.
 *  - YouTube (watch, youtu.be, shorts, live, embed, music) -> https://www.youtube.com/watch?v=<id>  (verified)
 *  - Netflix title/watch -> https://www.netflix.com/title/<id>                                      (verified)
 *  - Prime Video / Hotstar -> the URL as shared                                                    (unverified)
 */
object LinkMapper {
    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val idRegex = Regex("""[A-Za-z0-9_-]{6,}""")

    fun toAppLink(sharedText: String): String? {
        val url = urlRegex.find(sharedText)?.value?.trimEnd('.', ',', ')', ']', '\'', '!', '?') ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }

        youtubeId(host, segments, uri.rawQuery)?.let { return "https://www.youtube.com/watch?v=$it" }
        if (host == "netflix.com" || host.endsWith(".netflix.com")) return netflixLink(segments, url)
        if (host.endsWith("primevideo.com")) return url
        if (host.contains("amazon.") && segments.take(2) == listOf("gp", "video")) return url
        if (host == "hotstar.com" || host.endsWith(".hotstar.com") || host.endsWith("jiohotstar.com")) return url
        return null
    }

    private fun youtubeId(host: String, segments: List<String>, query: String?): String? {
        val id = when (host) {
            "youtu.be" -> segments.firstOrNull()
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when (segments.firstOrNull()) {
                "watch" -> queryParam(query, "v")
                "shorts", "live", "embed", "v" -> segments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { idRegex.matches(it) }
    }

    /** netflix.com/[locale/]title/<id> or /watch/<id> -> the verified title link; anything else as shared. */
    private fun netflixLink(segments: List<String>, original: String): String {
        val i = segments.indexOfFirst { it == "title" || it == "watch" }
        val id = segments.getOrNull(i + 1)?.takeIf { i >= 0 && it.all(Char::isDigit) }
        return if (id != null) "https://www.netflix.com/title/$id" else original
    }

    private fun queryParam(query: String?, name: String): String? =
        query?.split('&')?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == name }?.get(1)
}
