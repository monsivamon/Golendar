// ビルドに使用するプラグイン（Android・Kotlin・Compose・KSP）を適用する
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Androidアプリ全体のビルド設定
android {
    // アプリのパッケージ名前空間
    namespace = "com.monsivamon.golender"
    // コンパイルに使用するSDKバージョン
    compileSdk = 35

    // 使用するNDKのバージョン
    ndkVersion = "27.3.13750724"

    // アプリの基本情報（ID・SDK・バージョン・ABIなど）
    defaultConfig {
        // アプリケーションID
        applicationId = "com.monsivamon.golender"
        // 最小対応SDKバージョン
        minSdk = 29
        // 対象SDKバージョン
        targetSdk = 35
        // バージョンコード
        versionCode = 1
        // バージョン名
        versionName = "1.1.6"

        // 計装テストのランナー
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 対応するCPUアーキテクチャ（ABI）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // 16KBページサイズ対応を有効化するリンカーフラグ
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }

        // 同梱する言語リソースを日本語・英語に限定する
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

    // デバッグ・リリース各ビルドタイプの設定
    buildTypes {
        // デバッグビルドは圧縮・難読化なし
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        // リリースビルドは署名を付与して出力
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Lintチェックの挙動設定
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Java/Kotlinの互換性バージョン
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Composeのビルド機能を有効化する
    buildFeatures {
        compose = true
    }

    // KSP経由でRoomのスキーマJSON出力先を指定する
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // APKへの梱包ルール（JNI非圧縮・不要リソース除外）
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

// KotlinコンパイラのJVMターゲットを17に指定する
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 依存ライブラリの定義
dependencies {
    // Compose BOMでCompose系のバージョンを一括管理する
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ComposeのUI・グラフィック・プレビュー・Material3
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Android標準系ライブラリ（Core・Lifecycle・Activity）
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ナビゲーションとDataStore
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Room（実行時・Kotlin拡張・KSPプロセッサ）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // GlanceウィジェットとWorkManager
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    // 地図表示（MapLibre Native）
    implementation("org.maplibre.gl:android-sdk:11.13.1")

    // 画像読み込み（Coil）
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 単体テスト・計装テスト用
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // デバッグビルド限定のツール（Composeテストマニフェスト・ツーリング）
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}