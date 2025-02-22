plugins {
    kotlin("jvm") version "{{kotlin_version}}"
    application
}

group "{{tld}}.{{author}}"
version "{{version}}"

repositories {
    mavenCentral()
}
val junitVersion = {{junit_version}}
val assertKVersion = "0.+"
val mockkVersion = "1.+"
val log4jVersion = "2.+"
val log4jKotlinApiVersion = "1.+"
{{ if eq template_type "cli" }}
val mordantVersion = "1.+"
val cliktVersion = "3.+"
{{ end }}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
    implementation "org.apache.logging.log4j:log4j-api-kotlin:$log4jKotlinApiVersion"
    implementation "org.apache.logging.log4j:log4j-api:$log4jVersion"
    implementation "org.apache.logging.log4j:log4j-core:$log4jVersion"
{{ if eq template_type "cli" }}    // cli
    implementation "com.github.ajalt:mordant:$mordantVersion"
    implementation "com.github.ajalt.clikt:clikt:$cliktVersion"
{{ end }}

    testImplementation "io.mockk:mockk:$mockkVersion"
    testImplementation "org.junit.jupiter:junit-jupiter-api:$junitVersion"
    testImplementation "org.junit.jupiter:junit-jupiter-params:$junitVersion"
    testImplementation "com.willowtreeapps.assertk:assertk-jvm:$assertKVersion"

    runtimeOnly "org.junit.jupiter:junit-jupiter-engine:$junitVersion"
}

tasks.test {
    useJUnitPlatform()
}
