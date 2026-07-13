plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

pluginManager.withPlugin("java") {
    tasks.named("check") {
        dependsOn("koverVerify")
    }
}
