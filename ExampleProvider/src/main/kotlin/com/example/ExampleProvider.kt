package com.example

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class ExampleProvider(val plugin: TestPlugin) : MainAPI() {
    override var mainUrl = "https://vietsub.org"
    override var name = "Vietsuborg"
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)

    override var lang = "vi"

    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/search/$query"
        ).document
            .select("article")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    private fun Element.toSearchResponse(): MovieSearchResponse? {
        val link = this.select("div.halim-item a").last() ?: return null
        val href = link.attr("href")
        val title = link.attr("title")
//        val poster = fixUrl(this.selectFirst("img")?.attr("data-src"))
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

    private fun fixUrl(url: String?): String {
        if (url == null || url == "") {
            return "https://i.ebayimg.com/images/g/CwEAAOSwv4xf5cdv/s-l1200.jpg"
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        return mainUrl + url
    }
}