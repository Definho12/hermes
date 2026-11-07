import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "hermes"
version = "1.0.0"

kotlin { jvmToolchain(17) }

sourceSets {
    main {
        // the rules (formats, errors) and the icons are shared with the phone app, file for file
        kotlin.srcDir("../app/src/main/java/com/guilherme/hermes/shared")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    environment("HERMES_TEST_URL", System.getenv("HERMES_TEST_URL") ?: "")
    testLogging { events("failed", "passed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

compose.desktop {
    application {
        mainClass = "hermes.MainKt"
        jvmArgs += listOf(
            "-Xms24m", "-Xmx384m", "-XX:+UseSerialGC", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=25",
            "-XX:TieredStopAtLevel=1", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxMetaspaceSize=160m", "-Xss512k",
            "-Dfile.encoding=UTF-8"
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Hermes"
            // a new number every build, so the installer updates over the previous one
            packageVersion = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
            description = "Hermes - baixar videos e audios"
            vendor = "Guilherme"
            modules("java.desktop", "java.logging", "jdk.unsupported")
            windows {
                menuGroup = "Hermes"
                shortcut = true
                dirChooser = true
                upgradeUuid = "c4a7e2d9-5b13-4f86-9e2a-7d0b6f1c3e58"
                iconFile.set(project.file("icon/hermes.ico"))
            }
            // yt-dlp.exe and ffmpeg go here (the workflow downloads them)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("bundled"))
        }
    }
}
