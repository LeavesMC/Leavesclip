plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.leavesmc.org/releases/")
    maven("https://repo.leavesmc.org/snapshots/")
}

dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    implementation("io.sigpipe:jbsdiff:1.0")
    implementation("org.leavesmc:leaves-plugin-mixin-condition:1.0.0")
    implementation("io.github.llamalad7:mixinextras-common:0.5.0")
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains:annotations:15.0")
}

tasks.shadowJar {
    val prefix = "leavesclip.libs"
    listOf("org.apache", "org.tukaani", "io.sigpipe").forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }

    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE.txt")
}
