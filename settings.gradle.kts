pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.5"
}

dependencyResolutionManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
	}
}

rootProject.name = "minereels"

stonecutter {
	centralScript.set("build.gradle.kts")
	kotlinController.set(true)
	create(rootProject) {
		kotlinController.set(true)
		vcsVersion.set("1.21.11")
		versions("1.21.11", "26.1.2", "26.2")
	}
}
