package scu.dn.used_cars_backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

	private boolean success;
	private String code;
	private String message;
	private T data;
	private Object meta;

	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder()
				.success(true)
				.code("SUCCESS")
				.message("OK")
				.data(data)
				.build();
	}

	public static <T> ApiResponse<T> success(T data, Object meta) {
		return ApiResponse.<T>builder()
				.success(true)
				.code("SUCCESS")
				.message("OK")
				.data(data)
				.meta(meta)
				.build();
	}
}
