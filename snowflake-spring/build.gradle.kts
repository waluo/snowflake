plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    compile(project(":snowflake-core"))
}