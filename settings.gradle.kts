pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        gradlePluginPortal()
        mavenCentral()
    }
    
}
// settings.gradle.kts
plugins {
    id("org.gradle.toolchains.fake") // 占位，实际上 Gradle 会自动处理
}

// 声明构建所需的 Java 工具链（给构建脚本和插件用）
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
rootProject.name = "MetallumShaders"
