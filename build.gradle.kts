import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

kotlin {
    jvmToolchain(23)
}

javafx {
    version = "25.0.2"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.h2database:h2:2.3.232")
    implementation("io.github.nonoas:jfx-flat-ui:2.0.0-SNAPSHOT")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.pdmreader.app.PdmReaderLauncher")
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_23)
    }
}

tasks.test {
    useJUnitPlatform()
}
