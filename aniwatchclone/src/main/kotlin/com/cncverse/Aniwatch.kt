package com.cncverse

import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Aniwatch : MainAPI() {
    override var mainUrl = "https://aniwatch.co.at"
    override var name = "Aniwatch"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    companion object {
        private const val TAG = "Aniwatch"
    }

    override val mainPage = mainPageOf(
        "$mainUrl/recently-updated/?page=" to "Recently Updated",
        "$mainUrl/top-airing/?page=" to "Top Airing",
        "$mainUrl/most-popular-anime/?page=" to "Most Popular",
        "$mainUrl/new-anime/?page=" to "New On Aniwatch",
        "$mainUrl/latest-completed-anime/?page=" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val document = app.get(url).document
        val home = document.select("div.flw-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = this.selectFirst("h3.film-name a") ?: return null
        val title = titleEl.text().trim().ifBlank { return null }
        val href = fixUrl(titleEl.attr("href"))

        val posterUrl = this.selectFirst("img.film-poster-img")?.attr("data-src")
            ?: this.selectFirst("img.film-poster-img")?.attr("src")
            ?: this.selectFirst("img")?.attr("data-src")

        val subText = this.selectFirst("div.tick-sub")?.text()?.trim()?.toIntOrNull()
        val dubText = this.selectFirst("div.tick-dub")?.text()?.trim()?.toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(
                dubExist = dubText != null && dubText > 0,
                subExist = subText != null && subText > 0,
                dubEpisodes = dubText,
                subEpisodes = subText
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.flw-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h2.film-name")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.replace(Regex(" - Watch.*| \\| Aniwatch"), "")?.trim()
            ?: "Unknown"

        val poster = document.selectFirst("img.film-poster-img")?.attr("data-src")
            ?: document.selectFirst("img.film-poster-img")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val description = document.selectFirst("div.film-description div.text")?.text()?.trim()
            ?: document.selectFirst("div.description")?.text()?.trim()

        // Extract genres
        val tags = document.select("a[href*=/genre/]").map { it.text().trim() }
            .filter { it.isNotBlank() && it.length < 30 }
            .distinct()

        // Get episodes from the watch page episode list (ss-list)
        val subEpisodes = mutableListOf<Episode>()
        val dubEpisodes = mutableListOf<Episode>()

        // Episodes are on the episode/watch page, not the detail page
        // The detail page has a "Watch now" link to the first episode
        val watchNowLink = document.selectFirst("a[href*=episode]")?.attr("href")

        if (watchNowLink != null) {
            // Load the first episode page to get the full episode list
            try {
                val epPageDoc = app.get(fixUrl(watchNowLink)).document

                // Parse episode list from ss-list
                epPageDoc.select("div.ss-list a.ssl-item").forEach { epItem ->
                    val epNum = epItem.attr("data-number").toIntOrNull() ?: return@forEach
                    val epHref = epItem.attr("href").ifBlank { return@forEach }
                    val epTitle = epItem.attr("title").ifBlank { "Episode $epNum" }

                    subEpisodes.add(newEpisode(fixUrl(epHref)) {
                        this.name = epTitle
                        this.episode = epNum
                    })
                }

                // Check if dub servers exist on this page
                val hasDub = epPageDoc.selectFirst("div.servers-dub") != null
                if (hasDub) {
                    // Add same episodes as dub
                    subEpisodes.forEach { ep ->
                        dubEpisodes.add(newEpisode("${ep.data}|dub") {
                            this.name = ep.name
                            this.episode = ep.episode
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load episode list: ${e.message}")
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            this.posterUrl = poster
            this.plot = description
            this.tags = tags.ifEmpty { null }
            addEpisodes(DubStatus.Subbed, subEpisodes)
            if (dubEpisodes.isNotEmpty()) {
                addEpisodes(DubStatus.Dubbed, dubEpisodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data is episode URL, optionally with |dub suffix
        val isDub = data.endsWith("|dub")
        val episodeUrl = if (isDub) data.removeSuffix("|dub") else data

        val document = app.get(episodeUrl).document

        // Determine which server block to use
        val targetClass = if (isDub) "servers-dub" else "servers-sub"
        var serverBlock = document.selectFirst("div.$targetClass")

        // Fallback to any server block
        if (serverBlock == null) {
            serverBlock = document.selectFirst("div.player-servers")
        }

        // Find all server items with data-hash
        val serverItems = serverBlock?.select("div.server-item[data-hash]")
            ?: document.select("div.server-item[data-hash]")

        serverItems.forEach { server ->
            val hash = server.attr("data-hash")
            val serverName = server.attr("data-server-name")
            val serverType = server.attr("data-type")

            if (hash.isBlank()) return@forEach

            // Only process matching type
            if (isDub && serverType != "dub") return@forEach
            if (!isDub && serverType == "dub") return@forEach

            try {
                val decoded = String(Base64.decode(hash, Base64.DEFAULT)).trim()
                Log.d(TAG, "Server: $serverName ($serverType), Decoded: $decoded")

                val typeSuffix = if (isDub) "DUB" else "SUB"

                when {
                    // Direct MP4 player (my.1anime.site)
                    decoded.contains("1anime.site/index.php") -> {
                        val fileParam = Regex("[?&]file=([^&]+)").find(decoded)?.groupValues?.get(1)
                        if (fileParam != null) {
                            val mp4Url = "https://my.1anime.site/videos/$fileParam"
                            callback.invoke(
                                newExtractorLink(
                                    "Aniwatch $serverName [$typeSuffix]",
                                    "Aniwatch $serverName [$typeSuffix]",
                                    mp4Url,
                                    ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://my.1anime.site/"
                                    this.quality = Qualities.P1080.value
                                }
                            )
                        }
                    }
                    // MegaPlay-style streams (1anime.site/megaplay/stream/...)
                    decoded.contains("megaplay") -> {
                        OneAnimeExtractor().getUrl(decoded, mainUrl, subtitleCallback, callback)
                    }
                    // Vidup iframe (HTML with iframe tag)
                    decoded.contains("<iframe") -> {
                        val iframeSrc = Regex("""src="([^"]+)"""").find(decoded)?.groupValues?.get(1)
                        if (iframeSrc != null) {
                            loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)
                        }
                    }
                    // Generic URL
                    decoded.startsWith("http") -> {
                        loadExtractor(decoded, mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing server $serverName: ${e.message}")
            }
        }

        return true
    }
}
