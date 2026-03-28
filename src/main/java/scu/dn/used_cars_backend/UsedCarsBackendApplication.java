package scu.dn.used_cars_backend;

// Điểm vào Spring Boot — bật @EnableMethodSecurity cho @PreAuthorize (manager API).

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
@ConfigurationPropertiesScan
@EnableMethodSecurity
@EnableAsync
public class UsedCarsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(UsedCarsBackendApplication.class, args);
	}

}
