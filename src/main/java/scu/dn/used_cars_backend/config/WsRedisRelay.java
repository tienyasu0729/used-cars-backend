package scu.dn.used_cars_backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import scu.dn.used_cars_backend.dto.notification.WsNotificationEvent;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Conditional(NonBlankRedisHostCondition.class)
@RequiredArgsConstructor
public class WsRedisRelay implements MessageListener {

	private final ObjectMapper objectMapper;
	private final SimpMessagingTemplate messagingTemplate;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		try {
			if ("ws:admin:activity".equals(channel)) {
				WsNotificationEvent evt = objectMapper.readValue(body, WsNotificationEvent.class);
				messagingTemplate.convertAndSend("/topic/admin/activity", evt);
			} else if ("ws:user:notification".equals(channel)) {
				JsonNode root = objectMapper.readTree(body);
				String principal = root.path("principal").asText();
				WsNotificationEvent evt = objectMapper.treeToValue(root.get("event"), WsNotificationEvent.class);
				messagingTemplate.convertAndSendToUser(principal, "/queue/notifications", evt);
			}
		} catch (Exception e) {
			log.warn("redis ws relay: {}", e.toString());
		}
	}
}
