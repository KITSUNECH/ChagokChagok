import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// AI 키는 저장소에 올리지 않는다. 내 컴퓨터의 local.properties 에 anthropic.api.key=... 로 적어 둔다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.chagokchagok"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.chagokchagok"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${localProps.getProperty("anthropic.api.key", "")}\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module") } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.testLogging { showStandardStreams = true }; it.systemProperty("shots.dir", rootProject.file("build/shots").absolutePath) }
    }
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("com.anthropic:anthropic-java:3.3.0")   // 일기 문장을 다듬는 데 쓴다 (키가 있을 때만)
    testImplementation("junit:junit:4.13.2")
    // 화면을 이미지로 찍어 보는 테스트 (에뮬레이터 없이 실제 안드로이드 그리기 엔진으로)
    testImplementation("org.robolectric:robolectric:4.14.1")
}
