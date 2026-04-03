package scu.dn.used_cars_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@Conditional(NonBlankRedisHostCondition.class)
public class OptionalRedisConfig {

	@Bean
	LettuceConnectionFactory redisConnectionFactory(@Value("${spring.data.redis.host}") String host,
			@Value("${spring.data.redis.port:6379}") int port) {
		return new LettuceConnectionFactory(host, port);
	}

	@Bean
	StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
		StringRedisTemplate t = new StringRedisTemplate();
		t.setConnectionFactory(cf);
		return t;
	}

	@Bean
	RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory cf, WsRedisRelay relay) {
		RedisMessageListenerContainer c = new RedisMessageListenerContainer();
		c.setConnectionFactory(cf);
		c.addMessageListener(relay, new PatternTopic("ws:*"));
		return c;
	}
}
