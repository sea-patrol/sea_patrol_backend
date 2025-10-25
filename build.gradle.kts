plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ru"
version = "0.0.1-SNAPSHOT"
description = "Sea patrol project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation ("org.springframework.boot:spring-boot-starter-security")
	implementation ("org.springframework.session:spring-session-core")
	implementation("org.mapstruct:mapstruct:1.6.3")
	implementation ("org.springframework.boot:spring-boot-starter-validation")
	implementation("io.jsonwebtoken:jjwt:0.9.1")
	implementation("javax.xml.bind:jaxb-api:2.3.1")

	// LibGDX Core (вектора, коллекции, утилиты)
	implementation("com.badlogicgames.gdx:gdx:1.12.1")
	// Box2D Physics (с нативными библиотеками для desktop-ОС)
	implementation("com.badlogicgames.gdx:gdx-box2d:1.12.1")
	runtimeOnly("com.badlogicgames.gdx:gdx-box2d-platform:1.12.1:natives-desktop")
	// gdx-ai (ИИ: steering behaviors, pathfinding и т.д.)
	implementation("com.badlogicgames.gdx:gdx-ai:1.8.2")

	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
