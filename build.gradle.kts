plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.sauron"
version = "1.0.0"
description = "Blueprint marketplace plugin with originality detection, theft prevention, and royalties"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }

    val jarOutputDir = layout.projectDirectory.dir("jar")
    register<Copy>("copyJar") {
        dependsOn(shadowJar)
        from(shadowJar.get().archiveFile)
        into(jarOutputDir)
        doFirst { jarOutputDir.asFile.mkdirs() }
    }

    build {
        finalizedBy("copyJar")
    }
}