package com.cncverse

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimesugePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animesuge())
        registerExtractorAPI(AnimesugeMegaPlay())
    }
}
