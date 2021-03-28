import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.0-M1"
    kotlin("plugin.serialization") version "1.5.0-M1"
    application
}

application {
    mainClassName = "MainKt"
}

group = "me.mohamadhassan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    implementation("org.telegram:telegrambots:5.1.1")
    implementation("org.telegram:telegrambots-abilities:5.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"
}

tasks.create("stage") {
    dependsOn("installDist")
}