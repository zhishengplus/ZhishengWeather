import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

abstract class GenerateWidgetConfiguration : DefaultTask() {
    @get:Input abstract val applicationId: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val target = outputDirectory.file("values/widget_configuration.xml").get().asFile
        target.parentFile.mkdirs()
        target.writeText("<resources><string name=\"widget_configure_class\" translatable=\"false\">${applicationId.get()}.WidgetConfigure</string></resources>")
    }
}

abstract class GenerateLauncherShortcuts : DefaultTask() {
    @get:Input abstract val applicationId: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val template: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val target = outputDirectory.file("xml/shortcuts.xml").get().asFile
        target.parentFile.mkdirs()
        target.writeText(template.get().asFile.readText().replace("__APPLICATION_ID__", applicationId.get()))
    }
}

// 和风天气凭据（不入库，仅存 local.properties）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.canRead()) load(f.inputStream())
}
fun lp(key: String, def: String = ""): String = localProps.getProperty(key, def)

// 兼容旧命令的公开版开关；新发行流程优先使用独立的 assemblePublicRelease。
val publicBuild = providers.gradleProperty("publicBuild").isPresent
// Optional community contact configuration; no contact details are bundled.
val communityQqGroup = lp("community.qq_group")
val communityQqGroup1 = lp("community.qq_group_1")

android {
    namespace = "com.zhisheng.weather"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zhisheng.weather"
        minSdk = 26
        targetSdk = 34
        // Stable 0.1.6 community development baseline.
        versionCode = 20261172
        versionName = "0.1.6"

        // 发行渠道面向实体手机；去掉仅供 Intel 模拟器使用的 x86/x86_64 MapLibre 库。
        // 同时保留现代 ARM64 与 Android 8 时代的 32 位 ARM 设备。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        buildConfigField("String", "QW_HOST", "\"${if (publicBuild) "" else lp("qw.host")}\"")
        buildConfigField("String", "QW_PROJECT_ID", "\"${if (publicBuild) "" else lp("qw.project_id")}\"")
        buildConfigField("String", "QW_KID", "\"${if (publicBuild) "" else lp("qw.kid")}\"")
        buildConfigField("String", "QW_PRIVATE_KEY", "\"${if (publicBuild) "" else lp("qw.private_key")}\"")
        // 天地图移动端 Key：仅从被 Git 忽略的 local.properties 注入；包名与 SHA1 在控制台绑定。
        // 和风 JWT 在公开包必须清空；天地图 tk 按包名绑定，公开包同样需要底图。
        buildConfigField("String", "TDT_TOKEN", "\"${lp("tianditu.token")}\"")
        buildConfigField("String", "SHARED_SERVICE_ENDPOINT", "\"${lp("service.endpoint", "https://example.invalid")}\"")
        buildConfigField("String", "COMMUNITY_QQ_GROUP", "\"$communityQqGroup\"")
        buildConfigField("String", "COMMUNITY_QQ_GROUP_1", "\"$communityQqGroup1\"")
        buildConfigField("String", "COMMUNITY_QQ_JOIN_URL", "\"${lp("community.qq_join_url")}\"")
        buildConfigField("String", "COMMUNITY_QQ_JOIN_URL_1", "\"${lp("community.qq_join_url_1")}\"")
        // Local debug signatures cannot replace official release builds.
        buildConfigField("boolean", "CAN_SELF_UPDATE", "false")
        buildConfigField("boolean", "BADGE_EXPERIENCE", "false")
    }

    // Community builds use the developer's local debug signing key.

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        create("performance") {
            initWith(getByName("release"))
            // 真机性能验收：非调试构建，但沿用 debug 证书，可无损覆盖开发机上的 Debug 包。
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
        create("previewPublic") {
            initWith(getByName("release"))
            // 仅用于体验机并行安装：内容与公开版一致，但不覆盖手机上的满血版。
            val previewSuffix = providers.gradleProperty("previewSuffix").orElse(".preview").get()
            require(Regex("\\.[a-z][a-z0-9_]*").matches(previewSuffix)) { "Invalid preview application suffix" }
            applicationIdSuffix = previewSuffix
            resValue("string", "app_name", if (previewSuffix == ".preview") "枳生天气 澄空体验" else "枳生天气 分层验证")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "QW_HOST", "\"\"")
            buildConfigField("String", "QW_PROJECT_ID", "\"\"")
            buildConfigField("String", "QW_KID", "\"\"")
            buildConfigField("String", "QW_PRIVATE_KEY", "\"\"")
            buildConfigField("boolean", "CAN_SELF_UPDATE", "false")
        }
        create("publicRelease") {
            initWith(getByName("release"))
            // Optimized development build with empty QWeather compile-time credentials.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "QW_HOST", "\"\"")
            buildConfigField("String", "QW_PROJECT_ID", "\"\"")
            buildConfigField("String", "QW_KID", "\"\"")
            buildConfigField("String", "QW_PRIVATE_KEY", "\"\"")
            buildConfigField("boolean", "CAN_SELF_UPDATE", "false")
        }
        create("badgeExperience") {
            initWith(getByName("previewPublic"))
            applicationIdSuffix = ".badgeexperience"
            resValue("string", "app_name", "枳生徽章体验")
            buildConfigField("boolean", "BADGE_EXPERIENCE", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "mp4"
    }
    testOptions {
        // android.util.Log 等在纯 JVM 单测中返回默认值而非抛「not mocked」
        //（SourceHealth 熔断等逻辑的日志路径进入单测，v0.0.4）
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents.onVariants { variant ->
    val generateWidgetConfiguration = tasks.register<GenerateWidgetConfiguration>(
        "generate${variant.name.replaceFirstChar(Char::uppercase)}WidgetConfiguration",
    ) {
        applicationId.set(variant.applicationId)
        outputDirectory.set(layout.buildDirectory.dir("generated/widgetConfiguration/${variant.name}"))
    }
    variant.sources.res?.addGeneratedSourceDirectory(generateWidgetConfiguration, GenerateWidgetConfiguration::outputDirectory)
    val generateShortcuts = tasks.register<GenerateLauncherShortcuts>(
        "generate${variant.name.replaceFirstChar(Char::uppercase)}LauncherShortcuts",
    ) {
        applicationId.set(variant.applicationId)
        template.set(layout.projectDirectory.file("src/main/shortcuts.xml"))
        outputDirectory.set(layout.buildDirectory.dir("generated/launcherShortcuts/${variant.name}"))
    }
    variant.sources.res?.addGeneratedSourceDirectory(generateShortcuts, GenerateLauncherShortcuts::outputDirectory)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // The wardrobe same-anchor animation adapter targets the exact Material3 1.3.1 ABI.
    // Revisit that adapter and its gesture regression before upgrading either artifact.
    constraints {
        implementation("androidx.compose.material3:material3") { version { strictly("1.3.1") } }
        implementation("androidx.compose.material3:material3-android") { version { strictly("1.3.1") } }
    }
    implementation(libs.compose.foundation)
    implementation(libs.compose.tooling.preview)
    implementation(libs.core.ktx)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.bouncycastle)
    implementation("org.shredzone.commons:commons-suncalc:3.11")
    implementation(libs.maplibre.android)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.tooling)
}
