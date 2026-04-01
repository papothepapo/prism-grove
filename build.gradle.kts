plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.10" apply false
}

val tempBuildRoot = file("/tmp/prism-grove-build")
allprojects {
    layout.buildDirectory.set(tempBuildRoot.resolve(project.name))
}
