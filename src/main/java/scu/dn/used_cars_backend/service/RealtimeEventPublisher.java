package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.dto.notification.WsNotificationEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;
	private final ObjectMapper objectMapper;
	private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

	public void publishAdminActivity(WsNotificationEvent evt) {
		try {
			if (stringRedisTemplate.getIfAvailable() != null) {
				stringRedisTemplate.getIfAvailable().convertAndSend("ws:admin:activity", objectMapper.writeValueAsString(evt));
			} else {
				messagingTemplate.convertAndSend("/topic/admin/activity", evt);
			}
		} catch (JsonProcessingException e) {
			log.warn("publishAdminActivity failed: {}", e.toString());
		}
	}

	public void publishUserInbox(String userEmail, WsNotificationEvent evt) {
		try {
			if (stringRedisTemplate.getIfAvailable() != null) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("principal", userEmail);
				m.put("event", evt);
				stringRedisTemplate.getIfAvailable().convertAndSend("ws:user:notification", objectMapper.writeValueAsString(m));
			} else {
				messagingTemplate.convertAndSendToUser(userEmail, "/queue/notifications", evt);
			}
		} catch (JsonProcessingException e) {
			log.warn("publishUserInbox failed: {}", e.toString());
		}
	}
}
