plugins {
    java
    jacoco
    id("opssage.jacoco-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
}

tasks.test {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it).exclude("**/SreMcpServerAppKt.class")
            },
        ),
    )
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.observability)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.ai.mcp.server)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.bundles.spring.boot.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.datafaker)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
