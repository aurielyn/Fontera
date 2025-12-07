plugins {
	java
	kotlin("jvm")
	id("dev.deftu.gradle.multiversion")
	id("dev.deftu.gradle.tools")
	id("dev.deftu.gradle.tools.resources")
	id("dev.deftu.gradle.tools.bloom")
	id("dev.deftu.gradle.tools.shadow")
	id("dev.deftu.gradle.tools.minecraft.loom")
	id("dev.deftu.gradle.tools.minecraft.releases")
	id("dev.deftu.gradle.tools.publishing.maven")
}

apply(rootProject.file("secrets.gradle.kts"))

dependencies {
	modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.dependencies.fabric.fabricApiVersion}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${mcData.dependencies.fabric.fabricLanguageKotlinVersion}")
}

toolkitMavenPublishing {
	artifactName.set("fontera")
	setupRepositories.set(false)
}

java {
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	compilerOptions {
		apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
		languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
	}
}

afterEvaluate {
	publishing {
		publications {
			named<MavenPublication>("mavenJava") {
				pom {
					name.set("Fontera")
					description.set("A simple font rendering library.")
					url.set("https://github.com/aurielyn/fontera")
					licenses {
						license {
							name.set("GNU General Public License v3.0")
							url.set("https://www.gnu.org/licenses/gpl-3.0.en.html")
						}
					}
					developers {
						developer {
							id.set("aurielyn")
							name.set("Aurielyn")
						}
						developer {
							id.set("mrfast")
							name.set("MrFast")
						}
					}
					scm {
						url.set("https://github.com/aurielyn/fontera")
					}
				}
			}
		}
		repositories {
			maven {
				name = "Bundle"
				url = uri(layout.buildDirectory.dir("central-bundle"))
			}
		}
	}
}

signing {
	useGpgCmd()
	sign(publishing.publications)
}

val createBundle = tasks.register<Zip>("createBundle") {
	from(layout.buildDirectory.dir("central-bundle"))
	archiveFileName.set("fontera:$version")
	destinationDirectory.set(layout.buildDirectory)
	dependsOn("publishMavenJavaPublicationToBundleRepository")
}

tasks.register<Exec>("publishToSonatype") {
	group = "publishing"
	dependsOn(createBundle)
	commandLine(
		"curl", "-X", "POST",
		"-u", "${findProperty("sonatype.username")}:${findProperty("sonatype.password")}",
		"-F", "bundle=@${layout.buildDirectory.file("fontera:$version").get().asFile.absolutePath}",
		"https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
	)
}