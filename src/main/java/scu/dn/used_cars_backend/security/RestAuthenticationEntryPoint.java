package scu.dn.used_cars_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import scu.dn.used_cars_backend.common.exception.ErrorCode;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HttpErrorResponseWriter errorWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		errorWriter.write(response, ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.", request.getRequestURI());
	}
}
