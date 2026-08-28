import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val web3jCodegen: Configuration by configurations.creating

plugins {
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
}

group = "org.commonlink"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Spring Boot 4 uses Jackson 3 (tools.jackson.*) internally — the Jackson 2.x
    // jackson-module-kotlin never registers with it, so Kotlin default parameter values
    // are silently ignored and omitted fields NPE instead of falling back to their default.
    implementation("tools.jackson.module:jackson-module-kotlin")
    // JacksonConfig still hand-builds a classic com.fasterxml.jackson.databind.ObjectMapper
    // bean (used by OnchainJobWorker/PayoutConfirmer for outbox payload JSON) — that one
    // needs the Jackson 2.x kotlin module or Kotlin data classes fail with
    // "no Creators, like default constructor, exist".
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Same classic ObjectMapper also needs this for java.time types (e.g. Donation.donorBirthDate)
    // — without it, serialization throws InvalidDefinitionException on any LocalDate field.
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // SpringDoc OpenAPI (3.x required for Spring Boot 4 / Spring Framework 7)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // PDF generation (LGPL/MPL — not iText AGPL)
    implementation("com.github.librepdf:openpdf:1.3.35")

    // JWT (jjwt 0.12.x)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Google ID token verification
    implementation("com.google.api-client:google-api-client:2.7.2")

    // Web3j — on-chain registry interaction
    implementation("org.web3j:core:4.12.2")
    implementation("org.web3j:crypto:4.12.2")
    implementation("org.web3j:utils:4.12.2")

    // Fuzzy name matching — block-level phonetic + orthographic for LCB-FT sanctions screening
    implementation("org.apache.commons:commons-text:1.12.0")   // not in Spring BOM — pin explicitly
    implementation("commons-codec:commons-codec")               // DoubleMetaphone; version managed by Spring BOM

    // Web3j codegen — only used by generateRegistryWrapper task, not deployed
    web3jCodegen("org.web3j:codegen:4.12.2")

    // Tests
    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test") // if needed
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test") // ← NEW & IMPORTANT

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter")

}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.6")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        // -PnoDocker: exclude tests that need a Docker daemon (Testcontainers). Used by the
        // Clever Cloud deploy build, which has no Docker socket — see clevercloud/gradle.json.
        // Local `./gradlew.bat test` runs everything, unchanged.
        if (project.hasProperty("noDocker")) {
            excludeTags("testcontainers")
        }
    }
    // clevercloud/gradle.json builds with -quiet, which swallows Gradle's LIFECYCLE
    // task logs — println is raw stdout and shows up regardless, so this is the only
    // way to see in the Clever Cloud deploy logs that tests actually ran.
    afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
        if (desc.parent == null) {
            println("Test result: ${result.resultType} — ${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
        }
    }))
}

tasks.register<JavaExec>("generateRegistryWrapper") {
    group = "blockchain"
    description = "Generate the Web3j wrapper for CommonLinkRegistry from the Foundry artefact."
    mainClass.set("org.web3j.codegen.TruffleJsonFunctionWrapperGenerator")
    classpath = web3jCodegen

    val artefact = rootProject.file("../blockchain/out/CommonLinkRegistry.sol/CommonLinkRegistry.json")
    val processedDir = layout.buildDirectory.dir("web3jInput").get().asFile
    val processedFile = File(processedDir, "CommonLinkRegistry.json")
    val outDir = layout.projectDirectory.dir("src/main/java")

    // Preprocess at execution time: Foundry wraps bytecode as {object:"0x..."}, Truffle expects a plain string
    doFirst {
        processedDir.mkdirs()
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(artefact) as Map<*, *>
        val bytecodeStr = ((json["bytecode"] as? Map<*, *>)?.get("object") as? String) ?: ""
        val processed = mapOf(
            "contractName" to "CommonLinkRegistry",
            "abi" to json["abi"],
            "bytecode" to bytecodeStr,
        )
        processedFile.writeText(groovy.json.JsonOutput.toJson(processed))
    }

    args(
        "--javaTypes",
        "--outputDir", outDir.asFile.absolutePath,
        "--package", "org.commonlink.onchain.generated",
        processedFile.absolutePath,
    )
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val profile = project.findProperty("profile")?.toString() ?: "local"
    val envFile = file(".env.$profile")
    require(envFile.exists()) { "Missing env file: ${envFile.path}" }
    envFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            environment(key.trim(), value.trim())
        }
    args("--spring.profiles.active=$profile")
}
