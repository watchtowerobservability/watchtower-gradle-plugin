plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "io.github.watchtowerobservability"
version = (System.getenv("GITHUB_REF_NAME")?.takeIf { it.matches(Regex("v\\d.*")) } ?: "v0.1.0").removePrefix("v")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
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
    // Covers the plugin jar and the plugin-marker publication that makes `plugins { id(...) }` resolve.
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Watchtower Gradle Plugin")
            description.set("Uploads ProGuard/R8 mappings and native symbols to Watchtower and injects the release name")
            url.set("https://github.com/watchtowerobservability/watchtower-gradle-plugin")
            licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
            developers { developer { id.set("watchtower"); name.set("Watchtower"); url.set("https://github.com/watchtowerobservability") } }
            scm {
                url.set("https://github.com/watchtowerobservability/watchtower-gradle-plugin")
                connection.set("scm:git:https://github.com/watchtowerobservability/watchtower-gradle-plugin.git")
            }
        }
    }
    repositories {
        // Central Portal OSSRH staging API; credentials are CI secrets (repository absent without them).
        System.getenv("MAVEN_CENTRAL_USERNAME")?.takeIf { it.isNotBlank() }?.let { user ->
            maven("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/") {
                name = "MavenCentral"
                credentials {
                    username = user
                    password = System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
                }
            }
        }
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

// CI imports SIGNING_KEY into gpg; sign through the gpg binary (ed25519 safe).
signing {
    isRequired = !System.getenv("SIGNING_KEY").isNullOrBlank()
    if (isRequired) {
        useGpgCmd()
        sign(publishing.publications)
    }
}
tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(tasks.withType<Sign>()) }
