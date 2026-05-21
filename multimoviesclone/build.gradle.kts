// use an integer for version numbers
version = 48

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Indian Multi-language HD Provider"
    language = "hi"
    authors = listOf("Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
    )
    iconUrl = "https://raw.githubusercontent.com/LikDev-256/likdev256-tamil-providers/master/MultiMoviesProvider/icon.png"

    isCrossPlatform = false
}
