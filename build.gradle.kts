import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java")
    id("net.fabricmc.fabric-loom") version("1.17.13")
    id("maven-publish")
}

// 定义版本变量（从gradle.properties中读取）
val MINECRAFT_VERSION by extra { property("minecraft_version") as String }
val JAVA_VERSION by extra { property("java_version") as String }
val MOD_VERSION by extra { property("mod_version") as String }
val ARCHIVES_BASE_NAME by extra { property("archives_base_name") as String }
val LOADER_VERSION by extra { property("loader_version") as String }
val FABRIC_VERSION by extra { property("fabric_version") as String }
val MAVEN_GROUP by extra { property("maven_group") as String }

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

subprojects {
    apply(plugin = "maven-publish")

    java.toolchain.languageVersion = JavaLanguageVersion.of(JAVA_VERSION.toInt())

    fun createVersionString(): String {
        val builder = StringBuilder()

        val isReleaseBuild = project.hasProperty("build.release")
        val buildId = System.getenv("GITHUB_RUN_NUMBER")

        if (isReleaseBuild) {
            builder.append(MOD_VERSION)
        } else {
            builder.append(MOD_VERSION.substringBefore('-'))
            builder.append("-snapshot")
        }

        builder.append("+mc").append(MINECRAFT_VERSION)

        if (!isReleaseBuild) {
            if (buildId != null) {
                builder.append("-build.${buildId}")
            } else {
                builder.append("-local")
            }
        }

        return builder.toString()
    }

    tasks.processResources {
        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to createVersionString()))
        }
    }

    version = createVersionString()
    group = MAVEN_GROUP

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(JAVA_VERSION.toInt())
    }

    // Disables Gradle's custom module metadata from being published to maven
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }
}

// 原项目的特定配置
repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://libraries.minecraft.net/") }
    maven { url = uri("https://maven.caffeinemc.net/releases") }
}

loom {
    mods {
        register("metallum_shaders") {
            sourceSet("main")
        }
    }

    @Suppress("UnstableApiUsage")
    mixin {
        defaultRefmapName.set("metallum_shaders.refmap.json")
        useLegacyMixinAp = false
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${MINECRAFT_VERSION}")
    implementation("net.fabricmc:fabric-loader:${LOADER_VERSION}")
    implementation("net.fabricmc.fabric-api:fabric-api:${FABRIC_VERSION}")
    implementation(files("libs/metallum-1.0.1.jar"))
    compileOnly(files("libs/sodium-fabric-0.9.1+mc26.2.jar"))
}

base {
    archivesName.set(ARCHIVES_BASE_NAME)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(JAVA_VERSION.toInt())
    targetCompatibility = JavaVersion.toVersion(JAVA_VERSION.toInt())
}

// ===========================================================================
// Native build: 编译 JNI shim 为 libmetallum_shaders.dylib
// 仅在 macOS 上编译；非 macOS 跳过（jar 仍能构建，只是没有 dylib）。
// ===========================================================================

val nativeSrcDir = layout.projectDirectory.dir("src/main/cpp")
val nativeOutDir = layout.buildDirectory.dir("native")
val macosArmDylib = nativeOutDir.map { it.file("macos-arm64/libmetallum_shaders.dylib") }

val buildNativeArm64 by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile the Metal JNI shim for ARM64 (macOS only)."

    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }

    outputs.file(macosArmDylib)
    inputs.file(nativeSrcDir.file("metallum_shaders.cpp"))

    doFirst {
        val sdk = ProcessBuilder("xcrun", "--sdk", "macosx", "--show-sdk-path")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()

        val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")

        val outDir = nativeOutDir.get().asFile.resolve("macos-arm64")
        outDir.mkdirs()

        commandLine(
            "clang++",
            "-x", "objective-c++",
            "-std=c++17",
            "-stdlib=libc++",
            "-fobjc-arc",
            "-arch", "arm64",
            "-I", "$sdk/System/Library/Frameworks/Metal.framework/Headers",
            "-I", "$sdk/System/Library/Frameworks/Foundation.framework/Headers",
            "-I", "$javaHome/include",
            "-I", "$javaHome/include/darwin",
            "-F", "$sdk/System/Library/Frameworks",
            "-framework", "Metal",
            "-framework", "Foundation",
            "-shared",
            "-dynamiclib",
            "-o", macosArmDylib.get().asFile.absolutePath,
            nativeSrcDir.file("metallum_shaders.cpp").asFile.absolutePath
        )
    }
}

val buildNative by tasks.registering {
    group = "build"
    description = "Compile the Metal JNI shim (ARM64 only)."
    dependsOn(buildNativeArm64)
}

// 把 dylib 复制到 resources 目录，让 jar 自动打包
val copyNativeToResources by tasks.registering(Copy::class) {
    group = "build"
    dependsOn(buildNativeArm64)
    from(macosArmDylib)
    into(layout.projectDirectory.dir("src/main/resources/native/macos-arm64"))
}

tasks.named<Jar>("jar") {
    dependsOn(buildNativeArm64)
    from(macosArmDylib) {
        into("native/macos-arm64")
    }
}
