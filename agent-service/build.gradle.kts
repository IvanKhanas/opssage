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

val jacocoExcludes =
    listOf(
        "**/AgentServiceAppKt.class",
        "**/masking/OnnxNerPiiDetector.class",
    )

tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it).exclude(jacocoExcludes)
            },
        ),
    )
}

tasks
    .withType<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>()
    .configureEach {
        classDirectories.setFrom(
            files(
                classDirectories.files.map {
                    fileTree(it).exclude(jacocoExcludes)
                },
            ),
        )
    }

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.spring.boot.base)
    implementation(libs.bundles.observability)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.ai.openai.starter)
    implementation(libs.spring.ai.mcp.client)
    implementation(libs.bundles.djl.onnx.ner)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.spring.boot.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.datafaker)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
