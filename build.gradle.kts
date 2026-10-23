plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.59alex"
version = "0.2.0"

repositories { mavenCentral() }
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(27))
    withSourcesJar()
    withJavadocJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(27)
    options.encoding = "UTF-8"
}
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
}
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("relational-paths")
                description.set("Explainable join paths from runtime relational metadata")
                url.set("https://github.com/59Alex/relational-paths")
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { id.set("59Alex"); name.set("Aleksandr Kashevarov") } }
                scm {
                    connection.set("scm:git:https://github.com/59Alex/relational-paths.git")
                    url.set("https://github.com/59Alex/relational-paths")
                }
            }
        }
    }
}

val examples = sourceSets.create("examples") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
val example = tasks.register<JavaExec>("example") {
    group = "verification"
    description = "Runs the metadata-driven report query example"
    classpath = examples.runtimeClasspath
    mainClass.set("io.github.alex59.relationalpaths.examples.ReportQuery")
}
tasks.check { dependsOn(example) }

tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
}

// Custom JavaExec tasks must use the same JDK as compilation, even if Gradle runs on an older JVM.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(27))
    })
}
