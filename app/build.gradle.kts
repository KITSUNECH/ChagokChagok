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
    // 차의 주행 중 화면 제한(CarUxRestrictions)을 받으려고 플랫폼의 차 라이브러리를 쓴다
    useLibrary("android.car")
    namespace = "com.example.chagokchagok"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.chagokchagok"
        minSdk = 28
        targetSdk = 34
        versionCode = 10
        versionName = "0.3.7"
    }
    // 제출용 release: 공모전 안내대로 실행 가능한 release 버전. 서명은 공개 디버그 키로 해 별도 키 파일 없이 설치된다.
    // (Pleos App Market 탑재 때는 마켓 안내에 따른 정식 키로 바꾼다.) 코드 축소(R8)는 SDK 동작 확인 전이라 끈다.
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    // 판 나누기: contest(공모전 시연용 기능 포함, 기본은 꺼짐) · market(Pleos App Market 탑재용, 시연용·가상 데이터 없음)
    // Pleos 심사 가이드의 "테스트용 더미 데이터 금지"에 맞춰 market 판에는 시연 기능이 아예 들어가지 않는다.
    flavorDimensions += "edition"
    productFlavors {
        create("contest") { dimension = "edition"; buildConfigField("boolean", "DEMOS", "true") }
        create("market") { dimension = "edition"; buildConfigField("boolean", "DEMOS", "false"); versionNameSuffix = "-market" }
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
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")   // 기록과 키를 기기 안에서 암호화해 저장 (Pleos 보안 가이드)
    implementation("com.anthropic:anthropic-java:3.3.0")   // 일기 문장을 다듬는 데 쓴다 (키가 있을 때만)
    testImplementation("junit:junit:4.13.2")
    // 화면을 이미지로 찍어 보는 테스트 (에뮬레이터 없이 실제 안드로이드 그리기 엔진으로)
    testImplementation("org.robolectric:robolectric:4.14.1")
}
