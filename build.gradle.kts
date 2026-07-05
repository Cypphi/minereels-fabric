import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
	id("dev.kikugie.stonecutter")
	id("fabric-loom") version "1.17.13"
	id("maven-publish")
}

val minecraftVersion = property("minecraft_version").toString()
val yarnMappings = property("yarn_mappings").toString()
val loaderVersion = property("loader_version").toString()
val fabricVersion = property("fabric_version").toString()
val modVersion = property("mod_version").toString()
val mavenGroup = property("maven_group").toString()
val archivesBaseName = property("archives_base_name").toString()
val mappingsProvider = findProperty("mappings_provider")?.toString()
val minecraftRange = findProperty("minecraft_range")?.toString() ?: minecraftVersion
val minecraftDependency = findProperty("minecraft_dependency")?.toString() ?: minecraftVersion
val fabricDependency = findProperty("fabric_dependency")?.toString() ?: ">=$fabricVersion"
val javaRelease = findProperty("java_release")?.toString()?.toInt() ?: 21
val javaDependency = findProperty("java_dependency")?.toString() ?: ">=21"
val clientSourceSet = findProperty("client_source_set")?.toString() ?: minecraftVersion.filter(Char::isDigit)

// YACL + ModMenu aren't published for the 26.x snapshots yet, so the config UI
// (and its ModMenu entrypoint) is only wired for the older 1.21.x targets.
val yaclVersion = findProperty("yacl_version")?.toString() ?: ""
val modmenuVersion = findProperty("modmenu_version")?.toString() ?: ""
val quiltParsersVersion = findProperty("quilt_parsers_version")?.toString() ?: ""
val yaclDependency = findProperty("yacl_dependency")?.toString() ?: ">=3.8.2"
val modmenuDependency = findProperty("modmenu_dependency")?.toString() ?: ">=$modmenuVersion"
val isMc26 = minecraftVersion.startsWith("26.")

version = "$modVersion+mc$minecraftRange"
group = mavenGroup

base {
	archivesName.set(archivesBaseName)
}

repositories {
	maven {
		name = "Fabric"
		url = uri("https://maven.fabricmc.net/")
	}
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
	}
	maven {
		name = "Quilt"
		url = uri("https://maven.quiltmc.org/repository/release")
	}
	mavenCentral()
}

loom {
	splitEnvironmentSourceSets()

	runs {
		configureEach {
			runDir("../../run")
		}
	}

	mods {
		create("minereels") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	if (mappingsProvider == "none") {
		mappings(loom.layered {
			mappings(rootProject.file("versions/$minecraftVersion/identity.tiny")) {
				fallbackNamespaces("official", "named")
			}
		})
	} else {
		mappings("net.fabricmc:yarn:$yarnMappings:v2")
	}
	modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

	if (!isMc26) {
		modImplementation("maven.modrinth:yacl:$yaclVersion")
		modImplementation("maven.modrinth:modmenu:$modmenuVersion")
		modRuntimeOnly("org.quiltmc.parsers:gson:$quiltParsersVersion")
		modRuntimeOnly("org.quiltmc.parsers:json:$quiltParsersVersion")
	}
}

sourceSets {
	named("client") {
		// Per-version dirs keep mojmap (26.x) and yarn (1.21.x) client code
		// separate, since the Minecraft class names differ between them.
		java.setSrcDirs(listOf(rootProject.file("src/client/$clientSourceSet/java")))
	}
}

tasks.processResources {
	inputs.property("version", project.version)
	inputs.property("loader_dependency", ">=$loaderVersion")
	inputs.property("minecraft_dependency", minecraftDependency)
	inputs.property("java_dependency", javaDependency)
	inputs.property("fabric_dependency", fabricDependency)
	inputs.property("yacl_dependency", yaclDependency)
	inputs.property("modmenu_dependency", modmenuDependency)
	inputs.property("is_mc_26", isMc26)

	filesMatching("fabric.mod.json") {
		expand(mapOf("version" to project.version))
	}

	// Set dependency ranges and strip the config UI on targets without YACL.
	doLast {
		val modJson = destinationDir.resolve("fabric.mod.json")
		@Suppress("UNCHECKED_CAST")
		val metadata = JsonSlurper().parse(modJson) as MutableMap<String, Any?>

		@Suppress("UNCHECKED_CAST")
		val depends = metadata["depends"] as MutableMap<String, Any?>
		depends["fabricloader"] = ">=$loaderVersion"
		depends["minecraft"] = minecraftDependency
		depends["java"] = javaDependency
		depends["fabric-api"] = fabricDependency

		@Suppress("UNCHECKED_CAST")
		val entrypoints = metadata["entrypoints"] as MutableMap<String, Any?>

		if (isMc26) {
			entrypoints.remove("modmenu")
			depends.remove("yet_another_config_lib_v3")
			depends.remove("modmenu")
		} else {
			depends["yet_another_config_lib_v3"] = yaclDependency
			depends["modmenu"] = modmenuDependency
		}

		modJson.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(javaRelease)
}

if (mappingsProvider == "none") {
	tasks.matching { it.name in setOf("sourcesJar", "remapSourcesJar") }.configureEach {
		enabled = false
	}
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.named<Jar>("jar") {
	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}" }
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = archivesBaseName
			from(components["java"])
		}
	}
}
