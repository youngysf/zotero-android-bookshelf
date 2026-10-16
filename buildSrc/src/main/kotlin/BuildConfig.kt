object BuildConfig {
    // Bookshelf: own app ID so it installs alongside the official Zotero app.
    const val appId = "org.zotero.android.bookshelf"
    const val minSdkVersion = 23
    const val compileSdkVersion = 37
    const val targetSdk = 36

    const val versionCode = 289 // Must be updated on every build
    val version = Version(
        major = 1,
        minor = 0,
        patch = 0,
        versionCode = versionCode,
    )
}

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val versionCode: Int,
) {
    val name = "$major.$minor.$patch-$versionCode"
}
