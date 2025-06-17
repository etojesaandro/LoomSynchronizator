plugins {
    java
}

group = "org.saa"
version = "0.0.1"

repositories {
    mavenCentral()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.saa.Main"
    }
}

tasks.test {
    useJUnitPlatform()
}