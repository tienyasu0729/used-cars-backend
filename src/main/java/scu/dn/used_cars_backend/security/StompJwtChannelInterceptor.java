package scu.dn.used_cars_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

	private final JwtService jwtService;
	private final UserRepository userRepository;

	@Override
	public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}
		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			List<String> headers = accessor.getNativeHeader("Authorization");
			String token = null;
			if (headers != null && !headers.isEmpty()) {
				String h = headers.get(0);
				if (h != null && h.startsWith("Bearer ")) {
					token = h.substring(7).trim();
				}
			}
			if (token == null || token.isBlank()) {
				throw new AccessDeniedException("STOMP thiếu Authorization");
			}
			try {
				Claims claims = jwtService.parseClaims(token);
				Long userId = Long.parseLong(claims.getSubject());
				String roleName = claims.get("role", String.class);
				if (roleName == null || roleName.isBlank()) {
					throw new AccessDeniedException("Token không hợp lệ");
				}
				User user = userRepository.findByIdAndDeletedFalse(userId).orElseThrow(() -> new AccessDeniedException("User không tồn tại"));
				if (!"active".equalsIgnoreCase(user.getStatus())) {
					throw new AccessDeniedException("Tài khoản bị khóa");
				}
				String authority = "ROLE_" + roleName.toUpperCase().replace(' ', '_');
				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null,
						List.of(new SimpleGrantedAuthority(authority)));
				auth.setDetails(userId);
				accessor.setUser(auth);
			} catch (JwtException | IllegalArgumentException ex) {
				throw new AccessDeniedException("Token không hợp lệ");
			}
		} else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			var principal = accessor.getUser();
			String dest = accessor.getDestination();
			if (dest != null && dest.startsWith("/topic/admin/activity")) {
				if (!(principal instanceof Authentication auth) || auth.getAuthorities().stream()
						.noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
					throw new AccessDeniedException("Chỉ ADMIN được subscribe topic này");
				}
			}
		}
		return message;
	}
}
