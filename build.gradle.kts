
plugins {
    base
    kotlin("jvm") version "1.3.50" apply false
}

allprojects {
    group = "org.waluo"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

dependencies {
    // Make the root project archives configuration depend on every sub-project
    subprojects.forEach {
        archives(it)
    }
}