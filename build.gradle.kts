plugins {
    kotlin("multiplatform") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation("org.jetbrains.compose.html:html-core:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
    }
}

fun String.jsString(): String = replace("\\", "\\\\").replace("\"", "\\\"")

tasks.named("jsBrowserProductionWebpack") {
    doLast {
        val outputDir = layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable").get().asFile
        val resourcesDir = layout.buildDirectory.dir("processedResources/js/main").get().asFile
        resourcesDir.listFiles()?.filter { it.extension in listOf("html", "css", "ico", "png", "svg", "js") }?.forEach {
            it.copyTo(File(outputDir, it.name), overwrite = true)
        }
        resourcesDir.resolve("flags").takeIf { it.exists() }?.copyRecursively(
            target = File(outputDir, "flags"),
            overwrite = true,
        )

        File(outputDir, "config.js").writeText(
            """
            window.PORRAWEB_CONFIG = {
              supabaseUrl: "${System.getenv("SUPABASE_URL").orEmpty().jsString()}",
              supabasePublishableKey: "${System.getenv("SUPABASE_PUBLISHABLE_KEY").orEmpty().jsString()}"
            };
            """.trimIndent(),
        )
    }
}
