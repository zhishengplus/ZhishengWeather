import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 和风天气凭据（不入库，仅存 local.properties）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.canRead()) load(f.inputStream())
}
fun lp(key: String, def: String = ""): String = localProps.getProperty(key, def)

// 兼容旧命令的公开版开关；新发行流程优先使用独立的 assemblePublicRelease。
val publicBuild = providers.gradleProperty("publicBuild").isPresent
// 用户交流入口是公开信息，所有构建版本都必须包含。
val communityQqGroup = "1106284779"

android {
    namespace = "com.zhisheng.weather"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zhisheng.weather"
        minSdk = 26
        targetSdk = 34
        // 20260828：0.1.4 开发版
        versionCode = 20260838
        versionName = "0.1.4"

        buildConfigField("String", "QW_HOST", "\"${if (publicBuild) "" else lp("qw.host")}\"")
        buildConfigField("String", "QW_PROJECT_ID", "\"${if (publicBuild) "" else lp("qw.project_id")}\"")
        buildConfigField("String", "QW_KID", "\"${if (publicBuild) "" else lp("qw.kid")}\"")
        buildConfigField("String", "QW_PRIVATE_KEY", "\"${if (publicBuild) "" else lp("qw.private_key")}\"")
        buildConfigField("String", "COMMUNITY_QQ_GROUP", "\"$communityQqGroup\"")
        // 只有与 GitHub 公共版同包名、同签名的构建可以直接覆盖更新。
        buildConfigField("boolean", "CAN_SELF_UPDATE", publicBuild.toString())
    }

    signingConfigs {
        create("public") {
            // 公开证书只保证公开包可持续升级，密码本身不作为秘密。
            storeFile = project.rootProject.file("keystore/public.jks")
            storePassword = "public123"
            keyAlias = "public"
            keyPassword = "public123"
        }
        create("release") {
            val props = Properties()
            val f = rootProject.file("local.properties")
            if (f.canRead()) props.load(f.inputStream())
            if (publicBuild) {
                // 公开版：随库公开证书（密码公开即其设计，仅保证安装/升级签名一致）
                storeFile = project.rootProject.file("keystore/public.jks")
                storePassword = "public123"
                keyAlias = "public"
                keyPassword = "public123"
            } else {
                storeFile = project.rootProject.file("keystore/zhisheng.jks")
                storePassword = props.getProperty("keystore.store_password")
                keyAlias = "zhisheng"
                keyPassword = props.getProperty("keystore.key_password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "枳生天气 公开版")
            signingConfig = signingConfigs.getByName("public")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "QW_HOST", "\"\"")
            buildConfigField("String", "QW_PROJECT_ID", "\"\"")
            buildConfigField("String", "QW_KID", "\"\"")
            buildConfigField("String", "QW_PRIVATE_KEY", "\"\"")
            buildConfigField("boolean", "CAN_SELF_UPDATE", "false")
        }
        create("publicRelease") {
            initWith(getByName("release"))
            // 面向社区的正式公开包：独立任务、公开签名、凭据硬清空，避免漏写 -PpublicBuild。
            signingConfig = signingConfigs.getByName("public")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "QW_HOST", "\"\"")
            buildConfigField("String", "QW_PROJECT_ID", "\"\"")
            buildConfigField("String", "QW_KID", "\"\"")
            buildConfigField("String", "QW_PRIVATE_KEY", "\"\"")
            buildConfigField("boolean", "CAN_SELF_UPDATE", "true")
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
    testOptions {
        // android.util.Log 等在纯 JVM 单测中返回默认值而非抛「not mocked」
        //（SourceHealth 熔断等逻辑的日志路径进入单测，v0.0.4）
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.tooling.preview)
    implementation(libs.core.ktx)
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
    implementation(libs.work.runtime.ktx)
    implementation(libs.maplibre.android)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.tooling)
}
