plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("rustEngine") {
            id = "fabled.quitewhisper.rust-engine"
            implementationClass = "plugins.RustEnginePlugin"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // libs are not available here
    implementation("com.android.tools.build:gradle:9.2.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
}
