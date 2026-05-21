// use an integer for version numbers
version = 1

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}

cloudstream {
    language = "ta"

    description = "Netflix, PrimeVideo, Disney+ Hotstar Contents in Multiple Languages"
    authors = listOf("sevouz")

    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    requiresResources = true

    iconUrl = "https://github.com/sevouz/netclone/raw/refs/heads/master/netclone/icon.png"
}
