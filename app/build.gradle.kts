// プラグインの適用（Android、Kotlin、Compose、KSP）
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Androidアプリのビルド設定
android {
    namespace = "com.monsivamon.golender"
    compileSdk = 35

    // NDKバージョンを指定
    ndkVersion = "27.3.13750724"

    // アプリの基本設定（ID、SDKバージョン、バージョン情報）
    defaultConfig {
        applicationId = "com.monsivamon.golender"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 対応するABIを指定
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // 16KB ELFアライメントを有効化するリンカーフラグ
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }

        // 日本語と英語以外の言語リソースを除外
        resourceConfigurations += listOf("ja", "en")
    }

    // リリースビルド用の署名設定
    signingConfigs {
        create("release") {
            storeFile = file("../ks_pkcs12.keystore")
            storePassword = "123456789"
            keyAlias = "jhc"
            keyPassword = "123456789"
        }
    }

    // ビルドタイプの設定
    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Lintチェックの設定
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Java/Kotlinの互換性バージョン設定
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ビルド機能の有効化（Compose）
    buildFeatures {
        compose = true
    }

    // KSPでRoomのスキーマ出力先を指定
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // APK内の不要ファイル除去・ネイティブライブラリの非圧縮格納
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "kotlin/**",
            )
        }
    }
}

// KotlinコンパイラのJVMターゲット指定
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 依存ライブラリの定義
dependencies {
    // Compose BOMでバージョンを一括管理
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Composeコアライブラリ
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Android標準ライブラリ
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ナビゲーションとDataStore
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Room（データベース）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Glance（ウィジェット）とWorkManager（バックグラウンド更新）
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    // 地図表示（MapLibre Native）
    implementation("org.maplibre.gl:android-sdk:11.13.1")

    // テスト用
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // デバッグ時のみのツール
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}