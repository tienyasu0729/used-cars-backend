package scu.dn.used_cars_backend.sms.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.sms.entity.DeviceKey;
import scu.dn.used_cars_backend.sms.repository.DeviceKeyRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class DeviceKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_KEY = "X-Device-Key";
    private static final String REDIS_IP_FAIL_PREFIX = "sms:ip:fail:";
    private static final String REDIS_IP_BLOCK_PREFIX = "sms:ip:block:";
    private static final int MAX_INVALID_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration FAIL_LIST_TTL = Duration.ofMinutes(5);

    private final DeviceKeyRepository deviceKeyRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public DeviceKeyFilter(DeviceKeyRepository deviceKeyRepository,
                           ObjectMapper objectMapper,
                           ObjectProvider<StringRedisTemplate> redisProvider) {
        this.deviceKeyRepository = deviceKeyRepository;
        this.objectMapper = objectMapper;
        this.redisProvider = redisProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/sms/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = HttpServletClientIp.resolve(request);

        if (isIpBlocked(ip)) {
            writeError(response, 429, "IP blocked due to too many invalid attempts");
            return;
        }

        String deviceKeyHeader = request.getHeader(HEADER_DEVICE_KEY);

        if (deviceKeyHeader == null || deviceKeyHeader.isBlank()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Device-Key");
            return;
        }

        Optional<DeviceKey> optionalKey = deviceKeyRepository.findByDeviceKeyAndIsActiveTrue(deviceKeyHeader);

        if (optionalKey.isEmpty()) {
            recordInvalidAttempt(ip);
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Invalid device key");
            return;
        }

        DeviceKey key = optionalKey.get();
        key.setLastUsedAt(Instant.now());
        deviceKeyRepository.save(key);

        clearFailCount(ip);

        filterChain.doFilter(request, response);
    }

    private boolean isIpBlocked(String ip) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return false;
            }
            String blockKey = REDIS_IP_BLOCK_PREFIX + ip;
            return Boolean.TRUE.equals(redis.hasKey(blockKey));
        } catch (Exception e) {
            return false;
        }
    }

    private void recordInvalidAttempt(String ip) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return;
            }
            String failKey = REDIS_IP_FAIL_PREFIX + ip;
            Long count = redis.opsForList().rightPush(failKey, Instant.now().toString());
            redis.expire(failKey, FAIL_LIST_TTL);

            if (count != null && count >= MAX_INVALID_ATTEMPTS) {
                String blockKey = REDIS_IP_BLOCK_PREFIX + ip;
                redis.opsForValue().set(blockKey, "1", BLOCK_DURATION);
                redis.delete(failKey);
            }
        } catch (Exception ignored) {
        }
    }

    private void clearFailCount(String ip) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return;
            }
            String failKey = REDIS_IP_FAIL_PREFIX + ip;
            redis.delete(failKey);
        } catch (Exception ignored) {
        }
    }

    private void writeError(HttpServletResponse response, int status, String errorMessage) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", errorMessage));
    }
}
