package scu.dn.used_cars_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UsedCarsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(UsedCarsBackendApplication.class, args);
	}

}
