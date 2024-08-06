package com.example

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class ExampleProvider(val plugin: TestPlugin) : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://phimmoichillv.net/"
    override var name = "Vietsuborg"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "vi"

    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/tim-kiem/$query"
        ).document
            .select("li.item")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    private fun Element.toSearchResponse(): MovieSearchResponse? {
        val link = this.select("a").last() ?: return null
        val href = fixUrl(link.attr("href"))
        val title = fixUrl(link.attr("title"))
        val img = this.selectFirst("img")

        return MovieSearchResponse(
            img?.attr("alt")?.replaceFirst("Xem ", "") ?: return null,
            href,
            title,
            TvType.Movie,
            fixUrl(img.attr("src"))
        )
    }

    private fun fixUrl(url: String): String {
        return "https://phimmoichillv.net$url"
    }

}