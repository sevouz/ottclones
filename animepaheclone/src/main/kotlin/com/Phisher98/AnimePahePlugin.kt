package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    SI("https://animepahe.pw" to true),

    ORG("https://animepahe.org" to true),
    BEST("https://animepahe.com" to true)
}

@CloudstreamPlugin
class AnimePaheProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AnimePahe())
        registerExtractorAPI(Kwik())
        registerExtractorAPI(Pahe())

        val activity = context as AppCompatActivity
        openSettings = {
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        private const val PREF_KEY = "ANIMEPAHE_CURRENT_SERVER"
        var currentAnimepaheServer: String = ServerList.BEST.link.first
    }
}
