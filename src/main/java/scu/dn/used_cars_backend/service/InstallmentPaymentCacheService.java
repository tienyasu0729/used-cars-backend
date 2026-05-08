package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.dto.installment.InstallmentApplicationResponse;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentPaymentCacheService {

	private final ObjectProvider<StringRedisTemplate> redisProvider;
	private final ObjectMapper objectMapper;

	@Value("${app.installment.payment-cache-ttl-minutes:180}")
	private long paymentCacheTtlMinutes;

	public void saveSnapshot(InstallmentApplicationResponse data) {
		if (data == null || data.getId() == null || data.getCustomerId() == null) {
			return;
		}
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis == null) {
			return;
		}
		try {
			String raw = objectMapper.writeValueAsString(data);
			Duration ttl = Duration.ofMinutes(Math.max(1, paymentCacheTtlMinutes));
			String appKey = appKey(data.getCustomerId(), data.getId());
			redis.opsForValue().set(appKey, raw, ttl);
			if (data.getPreDepositId() != null) {
				redis.opsForValue().set(depositKey(data.getCustomerId(), data.getPreDepositId()), raw, ttl);
			}
			if (data.getDepositId() != null) {
				redis.opsForValue().set(depositKey(data.getCustomerId(), data.getDepositId()), raw, ttl);
			}
		} catch (JsonProcessingException e) {
			log.warn("Installment cache serialize fail appId={}: {}", data.getId(), e.getMessage());
		} catch (Exception e) {
			log.warn("Installment cache save skipped appId={}: {}", data.getId(), e.getMessage());
		}
	}

	public Optional<InstallmentApplicationResponse> getByApplicationId(Long customerId, Long applicationId) {
		if (customerId == null || applicationId == null) {
			return Optional.empty();
		}
		return read(appKey(customerId, applicationId));
	}

	public Optional<InstallmentApplicationResponse> getByDepositId(Long customerId, Long depositId) {
		if (customerId == null || depositId == null) {
			return Optional.empty();
		}
		return read(depositKey(customerId, depositId));
	}

	private Optional<InstallmentApplicationResponse> read(String key) {
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis == null) {
			return Optional.empty();
		}
		try {
			String raw = redis.opsForValue().get(key);
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			return Optional.ofNullable(objectMapper.readValue(raw, InstallmentApplicationResponse.class));
		} catch (Exception e) {
			log.warn("Installment cache read skipped key={}: {}", key, e.getMessage());
			return Optional.empty();
		}
	}

	private static String appKey(Long customerId, Long applicationId) {
		return "installment:payment-cache:user:" + customerId + ":app:" + applicationId;
	}

	private static String depositKey(Long customerId, Long depositId) {
		return "installment:payment-cache:user:" + customerId + ":deposit:" + depositId;
	}
}

