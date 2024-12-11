plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":analysis:analysis-api"))
    testImplementation(project(":analysis:analysis-api"))

    testImplementation(projectTests(":compiler:tests-common"))
    testImplementation(projectTests(":compiler:fir:raw-fir:psi2fir"))
    testImplementation(projectTests(":analysis:analysis-test-framework"))

    testCompileOnly(intellijCore())
    testRuntimeOnly(intellijCore())
}


sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
    }
}

projectTest {
    workingDir = rootDir
}

