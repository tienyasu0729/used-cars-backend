package scu.dn.used_cars_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtProperties jwtProperties;

	public String generateToken(Long userId, String email, String roleName) {
		long now = System.currentTimeMillis();
		Date expiry = new Date(now + jwtProperties.expirationMs());
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.claim("role", roleName)
				.issuedAt(new Date(now))
				.expiration(expiry)
				.signWith(signingKey())
				.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public Long extractUserId(String token) {
		return Long.parseLong(parseClaims(token).getSubject());
	}

	private SecretKey signingKey() {
		byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("JWT secret phải dài ít nhất 32 byte (256 bit) cho HS256.");
		}
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
