package scu.dn.used_cars_backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

	private Instant timestamp;
	private int status;
	private String errorCode;
	private String message;
	private String path;
	/** Chi tiết field khi VALIDATION_FAILED (theo ERROR_CODE_SPEC). */
	private List<FieldErrorDetail> errors;

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class FieldErrorDetail {
		private String field;
		private String message;
	}
}
