import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20"
    application
    id("org.jetbrains.kotlin.plugin.serialization") version "1.6.20"
    java
    id("com.bmuschko.docker-java-application") version "7.4.0"
}

group = "me.itanik"
version = "1.3-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("commons-net:commons-net:3.8.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

docker {
    javaApplication {
        baseImage.set("openjdk:8-jre-slim")
        maintainer.set("Nikita Ulyantsev 'yan.xhrome@gmail.com'")
        images.set(setOf("xhrome/fooduploadingbot:latest"))
    }
}

application {
    mainClass.set("MainKt")
}