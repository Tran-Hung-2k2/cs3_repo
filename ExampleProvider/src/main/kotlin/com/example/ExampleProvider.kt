package com.example

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class ExampleProvider(val plugin: TestPlugin) : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://vietsub.org/"
    override var name = "Vietsuborg"
    override val supportedTypes = setOf(TvType.Movie)

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
        val href = fixUrl(link.attr("href"))
        val title = fixUrl(link.attr("title"))
        val img = this.selectFirst("figure img")

        return MovieSearchResponse(
            img?.attr("alt")?.replaceFirst("Xem ", "") ?: return null,
            href,
            title,
            TvType.Movie,
            fixUrl(img.attr("src"))
        )
    }

    private fun fixUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        return mainUrl + url.removePrefix("/")
    }

}