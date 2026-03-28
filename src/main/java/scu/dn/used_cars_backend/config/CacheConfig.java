package scu.dn.used_cars_backend.config;

// Cấu hình cache Caffeine: vehicleList 5 phút, vehicleDetail 30 phút (VehicleService get/put/xóa tay qua CacheManager).

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

	@Bean
	CacheManager cacheManager() {
		SimpleCacheManager mgr = new SimpleCacheManager();
		mgr.setCaches(List.of(
				new CaffeineCache("vehicleList",
						Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(2000).build()),
				new CaffeineCache("vehicleDetail",
						Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(10000).build())));
		mgr.initializeCaches();
		return mgr;
	}

}
