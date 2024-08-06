package com.example

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder

class ExampleProvider(val plugin: TestPlugin) : MainAPI() {
    override var mainUrl = "https://vietsub.org"
    private var directUrl = mainUrl
    override var name = "Vietsub"
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)
    override var lang = "vi"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    //    MAIN PAGE
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Phim Mới",
        "$mainUrl/phim-le/page/" to "Phim Lẻ",
        "$mainUrl/phim-bo/page/" to "Phim Bộ",
        "$mainUrl/chieu-rap/page/" to "Phim Chiếu rạp",
        "$mainUrl/hoat-hinh/page/" to "Phim Hoạt hình",
        "$mainUrl/kinh-di/page/" to "Phim Kinh dị",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article").mapNotNull {
            it.toSearchResponse()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    //    SEARCH PAGE
    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/search/$query"
        ).document
            .select("article")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    private fun Element.toSearchResponse(): SearchResponse ? {
        val link = this.select("div.halim-item a").last() ?: return null
        val href = link.attr("href")
        val img = this.selectFirst("img")
        val posterUrl = img?.attr("data-src").toString()

        return MovieSearchResponse(
            img?.attr("alt")?.replaceFirst("Watch ", "") ?: return null,
            href,
            this@ExampleProvider.name,
            TvType.Movie,
            fixUrl(posterUrl),
        )
    }

    //    SHOW PAGE
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        directUrl = getBaseUrl(request.url)
        val document = request.document
        val vidUrl = document.selectFirst("video")?.attr("src").toString()

        val title = document.selectFirst("strong")?.text()?.trim().toString()
        val poster = document.selectFirst("div.jw-preview.jw-reset")?.attr("style")?.let {
            Regex("""url\(["']?([^"']*)["']?\)""").find(it)?.groups?.get(1)?.value
        }
        val tags = document.select("p.genres a").map { it.text() }
        val year = document.select(".year").text().trim().toIntOrNull()
        val tvType = if (document.select("#listsv-1 li").size > 1) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst(".item-content > p")?.text()?.trim().toString()
        val rating =
            document.select("span.score").text().toRatingInt()
        val actors = document.select("p.actors a").map { it.text() }
        val recommendations = document.select(".related-movies article").mapNotNull {
            it.toSearchResponse()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("#listsv-1 li").map {
                val href = it.select("a").attr("href")
                val episode =
                    it.select("a > span").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()
                val name = "Tập $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrl(poster)
                this.year = year
                this.plot = description + vidUrl
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.year = year
                this.plot = description + vidUrl
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val vidUrl = document.selectFirst("video")?.attr("src").toString()

        val key = document.select("div#content script")
            .find { it.data().contains("filmInfo.episodeID =") }?.data()?.let { script ->
                val id = script.substringAfter("parseInt('").substringBefore("'")
                app.post(
                    url = "$directUrl/chillsplayer.php",
                    data = mapOf("qcao" to id),
                    referer = data,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                ).text.substringAfterLast("iniPlayers(\"")
                    .substringBefore("\"")
            }

        listOf(
            Pair(vidUrl, "DEFAULT"),
            Pair("https://sotrim.topphimmoi.org/raw/$key/index.m3u8", "PMFAST"),
            Pair("https://dash.megacdn.xyz/raw/$key/index.m3u8", "PMHLS"),
            Pair("https://so-trym.phimchill.net/dash/$key/index.m3u8", "PMPRO"),
            Pair("https://dash.megacdn.xyz/dast/$key/index.m3u8", "PMBK")
        ).map { (link, source) ->
            callback.invoke(
                ExtractorLink(
                    source,
                    source,
                    link,
                    referer = "$directUrl/",
                    quality = Qualities.P1080.value,
                    INFER_TYPE,
                )
            )
        }
        return true
    }

    //    OTHER
    private fun fixUrl(url: String?): String {
        if (url == null || url == "") {
            return "https://www.reelviews.net/resources/img/default_poster.jpg"
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        return mainUrl + url
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")
}