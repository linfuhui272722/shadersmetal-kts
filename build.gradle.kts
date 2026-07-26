import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java")
    id("net.fabricmc.fabric-loom") version ("1.17.13")
    id("maven-publish")
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://libraries.minecraft.net/") }
    maven { url = uri("https://maven.caffeinemc.net/releases") }
}

loom {
    // 不使用 splitEnvironmentSourceSets()。
    // 所有代码（含客户端代码）都在 src/main/java/ 下，
    // main 源集需要看到 net.minecraft.client.* 类。
    // Loom 会自动把 Minecraft merged jar 加到 main 源集的编译类路径上。
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

// 确保编译时使用正确的 Java 版本
dependencies {
    // Minecraft 26.2 —— 无混淆，不需要 mappings 依赖。
    // Loom 会自动下载 MC jar、合并 client+server、加到 compileClasspath。
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    // Fabric Loader + Fabric API
    // 用 implementation（非 modImplementation）。MC 26.2 无混淆，
    // Loom 不需要做 remap，implementation 即可。
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Metallum —— Metal API 桥接 mod（本地 jar）
    implementation(files("libs/metallum-1.0.1.jar"))

    // Sodium —— 仅编译期依赖，不打包进 jar
    compileOnly(files("libs/sodium-fabric-0.9.1+mc26.2.jar"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of((property("java_version") as String).toInt())
        vendor = JvmVendorSpec.ADOPTIUM
    }
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion((property("java_version") as String).toInt())
    targetCompatibility = JavaVersion.toVersion((property("java_version") as String).toInt())
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 修复：必须使用 project.property(...)，否则会尝试读取 Task 的属性导致报错
    options.release.set((project.property("java_version") as String).toInt())
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
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
