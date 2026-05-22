version = 1

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

cloudstream {
    language = "en"

    description = "Watch Anime Online Free in HD (SUB/DUB)"
    authors = listOf("sevouz")

    status = 3
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "OVA",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=animesuge.re&sz=%size%"

    isCrossPlatform = false
}
