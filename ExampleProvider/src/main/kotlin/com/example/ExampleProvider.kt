package com.example

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import org.jsoup.nodes.Element

class ExampleProvider(val plugin: TestPlugin) : MainAPI() {
    override var mainUrl = "https://vietsub.org"
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
            "$mainUrl/search/$query/page/"
        ).document
            .select("article")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    private fun Element.toSearchResponse(): MovieSearchResponse? {
        val link = this.select("div.halim-item a").last() ?: return null
        val href = link.attr("href")
        val img = this.selectFirst("img")
        val posterUrl = img?.attr("data-src").toString()

        return MovieSearchResponse(
            img?.attr("alt")?.replaceFirst("Watch ", "") ?: return null,
            href,
            this@ExampleProvider.name,
            TvType.Movie,
            fixUrl(img.attr("data-src")),
        )
    }

    //    SHOW PAGE

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
}