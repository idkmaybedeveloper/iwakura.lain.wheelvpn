plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "iwakura.lain.catray"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to ".", "include" to listOf("*.aar"))))
}
