plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    application
}

version = System.getenv("VERSION_NAME") ?: "0.0.0"

dependencies {
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
    // dark Windows title bar (DwmSetWindowAttribute)
    implementation("net.java.dev.jna:jna:5.14.0")
}

application {
    mainClass.set("com.rayscope.desktop.MainKt")
    applicationName = "RayScope"
}
