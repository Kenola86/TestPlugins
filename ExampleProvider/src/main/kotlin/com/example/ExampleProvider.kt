package com.example

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

class ExampleProvider(val plugin: TestPlugin) : MainAPI() { // all providers must be an intstance of MainAPI
class Motherless : MainAPI() {
    override var mainUrl              = "https://motherless.com"
    override var name                 = "Motherless"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "" to "Recent Videos",
        "g/popular" to "Popular Videos",
        "g/top-rated" to "Top Rated",
        "g/amateur" to "Amateur",
        "g/teen" to "Teen",
        "g/webcam" to "Webcam",
        "g/hd" to "HD Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home = document.select("div.thumb-container").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = document.selectFirst("a.pagination_link.next") != null
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.selectFirst("a[href]")?.attr("href") ?: return null)
        val title = fixTitle(this.selectFirst("div.caption a")?.text() ?: "No Title").trim()
        val posterUrl = this.selectFirst("img")?.attr("src") ?: return null
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val subquery = query.replace(" ", "+")
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("$mainUrl/search/videos?term=$subquery&page=$i").document
            val results = document.select("div.thumb-container").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Sjekk for feilmeldinger
        if (document.selectFirst("title")?.text()?.contains("404") == true ||
            document.selectFirst("div.error-page")?.text()?.contains("File not found") == true) {
            throw RuntimeException("Video not found")
        }

        val title = document.selectFirst("h1#view-upload-title")?.text()?.trim() ?: "No Title"
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.description")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Sjekk for feilmeldinger
        if (document.selectFirst("title")?.text()?.contains("404") == true ||
            document.selectFirst("div.error-page")?.text()?.contains("File not found") == true ||
            document.text().contains("The content you are trying to view is for friends only")) {
            return false
        }

        val videoSource = document.selectFirst("video > source")?.attr("src")
            ?: throw RuntimeException("No video source found")

        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = videoSource,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                type = INFER_TYPE
            )
        )
        return true
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}
