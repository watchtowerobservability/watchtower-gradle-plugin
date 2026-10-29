// Minimal JVM app using the plugin. Run: WT_API_KEY=... ./gradlew watchtowerUpload
plugins {
    java
    id("io.github.watchtowerobservability.watchtower") version "0.1.0"
}

version = "1.0.0"

watchtower {
    url.set("https://watchtower.example.com")
    project.set("prj_123")
    mappingFiles.from(layout.buildDirectory.dir("proguard/mapping.txt"))
}
