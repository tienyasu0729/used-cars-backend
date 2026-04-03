package scu.dn.used_cars_backend.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class NonBlankRedisHostCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String host = context.getEnvironment().getProperty("spring.data.redis.host", "");
		return host != null && !host.isBlank();
	}
}
