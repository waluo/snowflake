plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("io.mockk:mockk:1.9.3")
}