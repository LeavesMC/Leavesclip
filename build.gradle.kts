import kotlin.system.exitProcess

plugins {
    java
    application
    `maven-publish`
}

subprojects {
    apply(plugin = "java")

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

val mainClass = "org.leavesmc.leavesclip.Main"

tasks.jar {
    val java6Jar = project(":java6").tasks.named("jar")
    val java17Jar = project(":java21").tasks.named("shadowJar")
    dependsOn(java6Jar, java17Jar)

    from(zipTree(java6Jar.map { it.outputs.files.singleFile }))
    from(zipTree(java17Jar.map { it.outputs.files.singleFile }))

    manifest {
        attributes(
            "Main-Class" to mainClass
        )
    }

    doFirst {
        val clipVerFile = File("leavesclip-version")
        if (!clipVerFile.exists()) {
            if(!clipVerFile.createNewFile()){
                println("failed to create file: leavesclip-version")
                exitProcess(1)
            }
        }
        clipVerFile.writeText(project.version.toString())
    }

    from(file("leavesclip-version")) {
        into("META-INF")
    }

    from(file("license.txt")) {
        into("META-INF/license")
        rename { "leavesclip-LICENSE.txt" }
    }

    rename { name ->
        if (name.endsWith("-LICENSE.txt")) {
            "META-INF/license/$name"
        } else {
            name
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    val java6Sources = project(":java6").tasks.named("sourcesJar")
    val java17Sources = project(":java21").tasks.named("sourcesJar")
    dependsOn(java6Sources, java17Sources)

    from(zipTree(java6Sources.map { it.outputs.files.singleFile }))
    from(zipTree(java17Sources.map { it.outputs.files.singleFile }))

    archiveClassifier.set("sources")
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
            artifact(sourcesJar)
            withoutBuildIdentifier()

            pom {
                val repoPath = "LeavesMC/Leavesclip"
                val repoUrl = "https://github.com/$repoPath"

                name.set("Leavesclip")
                description.set(project.description)
                url.set(repoUrl)
                packaging = "jar"

                licenses {
                    license {
                        name.set("MIT")
                        url.set("$repoUrl/blob/main/license.txt")
                        distribution.set("repo")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("$repoUrl/issues")
                }

                developers {
                    developer {
                        id.set("DemonWav")
                        name.set("Kyle Wood")
                        email.set("demonwav@gmail.com")
                        url.set("https://github.com/DemonWav")
                    }
                    developer {
                        id.set("MC_XiaoHei")
                        name.set("MC_XiaoHei")
                        email.set("xiaohei.xor7studio@foxmail.com")
                        url.set("https://github.com/MC_XiaoHei")
                    }
                }

                scm {
                    url.set(repoUrl)
                    connection.set("scm:git:$repoUrl.git")
                    developerConnection.set("scm:git:git@github.com:$repoPath.git")
                }
            }
        }

        repositories {
            val url = if (isSnapshot) {
                "https://repo.leavesmc.org/snapshots"
            } else {
                "https://repo.leavesmc.org/releases"
            }

            maven(url) {
                name = "leaves"
                credentials(PasswordCredentials::class) {
                    username = System.getenv("LEAVES_USERNAME")
                    password = System.getenv("LEAVES_PASSWORD")
                }
            }
        }
    }
}

tasks.register("printVersion") {
    doFirst {
        println(version)
    }
}
