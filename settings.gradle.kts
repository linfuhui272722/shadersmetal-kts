pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "fabric-loom") {
                useModule("net.fabricmc:fabric-loom:${requested.version}")
            }
        }
    }
}
gradle.beforeProject {
    buildscript {
        configurations.all {
            attributes {
                attribute(
                    org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                    21 // 假装我们需要 Java 21，这样就能匹配到 fabric-loom 的变体
                )
            }
        }
    }
}
rootProject.name = "MetallumShaders"
