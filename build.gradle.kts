plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

group = "dev.jacobandersen"
version = file("version.txt").readText().trim()
description = "forge"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "BastionGitHubPackages"
        url = uri("https://maven.pkg.github.com/marchland/bastion")
        credentials {
            username = System.getenv("PACKAGES_USER") ?: System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
            password = System.getenv("PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
        }
    }
    maven {
        name = "Microformats2GitHubPackages"
        url = uri("https://maven.pkg.github.com/marchland/microformats2")
        credentials {
            username = System.getenv("PACKAGES_USER") ?: System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
            password = System.getenv("PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
        }
    }
    maven {
        name = "SigilGitHubPackages"
        url = uri("https://maven.pkg.github.com/marchland/sigil")
        credentials {
            username = System.getenv("PACKAGES_USER") ?: System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
            password = System.getenv("PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
        }
    }
}

extra["microformats2Version"] = "0.1.2"
extra["contentClientVersion"] = "1.3.33"
extra["sigilVersion"] = "0.2.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("dev.jacobandersen:microformats2:${property("microformats2Version")}")
    implementation("dev.jacobandersen:content-client:${property("contentClientVersion")}")
    implementation("dev.jacobandersen:sigil-client:${property("sigilVersion")}")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("forge.jar")
}

ktlint {
    version.set("1.8.0")
}
