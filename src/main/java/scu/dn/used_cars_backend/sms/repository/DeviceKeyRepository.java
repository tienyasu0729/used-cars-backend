package scu.dn.used_cars_backend.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.sms.entity.DeviceKey;

import java.util.Optional;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, Long> {

	Optional<DeviceKey> findByDeviceKey(String deviceKey);

	Optional<DeviceKey> findByDeviceKeyAndIsActiveTrue(String deviceKey);
}
