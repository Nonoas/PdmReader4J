import io.github.fvarrui.javapackager.gradle.PackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val myMainClass: String by project
val javafxPreviewJvmArgs = listOf("-Djavafx.enablePreview=true")

buildscript {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
    dependencies {
        classpath("io.github.fvarrui:javapackager:1.7.5")
    }
}

plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("io.github.fvarrui.javapackager.plugin") version "1.7.5"
}

group = "indi.nonoas"
version = "0.0.1"

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
    implementation("org.json:json:20240303")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set(myMainClass)
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8") + javafxPreviewJvmArgs
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_23)
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(javafxPreviewJvmArgs)
}

tasks.register<PackageTask>("packageMyApp") {
    dependsOn(tasks.build)

    vmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        *javafxPreviewJvmArgs.toTypedArray(),
        "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"
    )

    mainClass = myMainClass

    modules = listOf(
        "java.base",
        "java.management",
        "java.net.http",
        "java.scripting",
        "java.sql",
        "java.naming",
        "jdk.jsobject",
        "jdk.unsupported",
        "jdk.unsupported.desktop",
        "jdk.xml.dom",
        "jdk.crypto.ec"
    )

    isBundleJre = true
    isGenerateInstaller = false
    isAdministratorRequired = false

    winConfig.apply {
        isCreateZipball = true
    }
}
