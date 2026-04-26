package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.SystemConfig;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Integer> {

	Optional<SystemConfig> findByConfigKey(String configKey);
}
