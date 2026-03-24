package scu.dn.used_cars_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import scu.dn.used_cars_backend.common.error.ApiErrorResponse;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class HttpErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public void write(HttpServletResponse response, ErrorCode errorCode, String message, String path)
			throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiErrorResponse body = ApiErrorResponse.builder()
				.timestamp(Instant.now())
				.status(errorCode.getHttpStatus().value())
				.errorCode(errorCode.getCode())
				.message(message)
				.path(path)
				.build();
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
