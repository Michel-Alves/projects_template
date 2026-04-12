plugins {
    kotlin("jvm") version "{{kotlin_version}}"
    application
}

group "{{tld}}.{{author}}"
version "{{version}}"

repositories {
    mavenCentral()
}

val junitVersion = "{{junit_version}}"
val assertKVersion = "0.+"
val mockkVersion = "1.+"
val log4jVersion = "2.+"
val log4jKotlinApiVersion = "1.+"

{{ if eq stack_profile "cli" }}
val mordantVersion = "1.+"
val cliktVersion = "3.+"
{{ end }}

{{ if or (eq stack_profile "web") (eq stack_profile "web-db") }}
val ktorVersion = "2.3.+"
{{ end }}

{{ if or (eq stack_profile "db") (eq stack_profile "web-db") }}
val exposedVersion = "0.50.+"
val hikariCpVersion = "5.+"
val flywayVersion = "10.+"
val postgresqlVersion = "42.+"
{{ end }}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:$log4jKotlinApiVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")

{{ if eq stack_profile "cli" }}
    implementation("com.github.ajalt:mordant:$mordantVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
{{ end }}

{{ if or (eq stack_profile "web") (eq stack_profile "web-db") }}
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
{{ end }}

{{ if or (eq stack_profile "db") (eq stack_profile "web-db") }}
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
{{ end }}

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertKVersion")

    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}
