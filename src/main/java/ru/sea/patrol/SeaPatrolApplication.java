package ru.sea.patrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SeaPatrolApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeaPatrolApplication.class, args);
	}

}