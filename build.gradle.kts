plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.watchtowerobservability"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("watchtower") {
            id = "io.github.watchtowerobservability.watchtower"
            implementationClass = "io.watchtower.gradle.WatchtowerPlugin"
            displayName = "Watchtower"
            description = "Uploads ProGuard/R8 mappings and native symbols to Watchtower and injects the release name"
        }
    }
}

tasks.test { useJUnitPlatform() }

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/watchtowerobservability/watchtower-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
