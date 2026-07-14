plugins {
    kotlin("jvm") version "2.3.21"
    application
}

group = "dev.naominet"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.naominet:purple-framework:1.0-SNAPSHOT")
    implementation("com.alibaba.fastjson2:fastjson2-kotlin:2.0.60")
    implementation("io.ktor:ktor-client-core:3.3.2")
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("RunFrameworkKt")
}

val preparePluginRun by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("plugins"))
}

tasks.named<JavaExec>("run") {
    dependsOn(preparePluginRun)
    workingDir = projectDir
}

tasks.test {
    useJUnitPlatform()
}
