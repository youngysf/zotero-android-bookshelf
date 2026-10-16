// Bookshelf: buildSrc otherwise resolves its own plugins (kotlin-dsl) from the
// Gradle Plugin Portal alone, and fails whenever the portal can't reach its
// Maven Central mirror. Listing Maven Central directly avoids that.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
