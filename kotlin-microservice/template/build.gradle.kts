plugins {
    kotlin("jvm") version "{{kotlin_version}}"
    kotlin("plugin.spring") version "{{kotlin_version}}"
    id("org.springframework.boot") version "{{spring_boot_version}}"
    id("io.spring.dependency-management") version "{{spring_dependency_management_version}}"
}

group = "{{tld}}.{{author}}"
version = "{{version}}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of({{java_version}}))
    }
}

repositories {
    mavenCentral()
}

val awsSdkVersion = "{{aws_sdk_version}}"
val otelVersion = "{{otel_version}}"
val log4jVersion = "{{log4j_version}}"
val micrometerVersion = "{{micrometer_version}}"
val testcontainersVersion = "{{testcontainers_version}}"

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:$awsSdkVersion")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:$otelVersion")
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }
}

configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("software.amazon.awssdk:sns")
    implementation("software.amazon.awssdk:sqs")

    implementation("org.apache.logging.log4j:log4j-layout-template-json:$log4jVersion")
{{- if eq stack_profile "relational-db" }}

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql:{{flyway_version}}")
    runtimeOnly("org.postgresql:postgresql")
{{- end }}
{{- if eq stack_profile "nosql-cache" }}

    implementation("org.mongodb:mongodb-driver-sync")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.mongock:mongock-springboot-v3:{{mongock_version}}")
    implementation("io.mongock:mongodb-sync-v4-driver:{{mongock_version}}")
{{- end }}

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testImplementation("com.tngtech.archunit:archunit-junit5:{{archunit_version}}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
{{- if eq stack_profile "relational-db" }}
    testImplementation("org.testcontainers:postgresql")
{{- end }}
{{- if eq stack_profile "nosql-cache" }}
    testImplementation("org.testcontainers:mongodb")
{{- end }}
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("{{java_version}}"))
    }
}

tasks.test {
    useJUnitPlatform()
}
