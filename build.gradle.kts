plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.59alex"
version = "0.1.0"

repositories { mavenCentral() }
java {
    withSourcesJar()
    withJavadocJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
}
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
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

val examples by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
val example by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the metadata-driven report query example"
    classpath = examples.runtimeClasspath
    mainClass.set("io.github.alex59.relationalpaths.examples.ReportQuery")
}
tasks.check { dependsOn(example) }

tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
}
