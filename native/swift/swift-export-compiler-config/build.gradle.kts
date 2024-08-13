plugins {
    kotlin("jvm")
    id("jps-compatible")
}

description = "Compiler config generated by Swift Export"

kotlin {
    explicitApi()
}

dependencies {
    implementation(kotlinStdlib())
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}