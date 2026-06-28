import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless")
}

configure<SpotlessExtension> {
    kotlin {
        targetExclude("build/**/*.kt")
        licenseHeaderFile(rootProject.file("gradle/license/header.txt"))
    }
}