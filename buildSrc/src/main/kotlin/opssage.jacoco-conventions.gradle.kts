import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
}

tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn(tasks.withType<JacocoReport>())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

pluginManager.withPlugin("java") {
    tasks.named("check") {
        dependsOn(tasks.withType<JacocoCoverageVerification>())
    }
}