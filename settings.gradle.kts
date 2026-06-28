rootProject.name = "opssage"

include("agent-service")
include("sre-mcp-server")
include("knowledge-mcp-server")
include("admin-service")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}