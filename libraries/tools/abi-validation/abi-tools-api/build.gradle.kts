plugins {
    kotlin("jvm")
}

kotlin {
    explicitApi()
}

configureKotlinCompileTasksGradleCompatibility()

publish()

standardPublicJars()

dependencies {
    compileOnly(kotlinStdlib())

    testImplementation(kotlinTest("junit"))
    testImplementation(libs.junit4)
}
