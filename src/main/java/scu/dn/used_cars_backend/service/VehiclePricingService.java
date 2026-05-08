package scu.dn.used_cars_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.PricingServiceProperties;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingEstimateRequest;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingImageAssetRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VehiclePricingService {

	private final PricingServiceProperties pricingProperties;
	private final ObjectMapper objectMapper;

	public VehiclePricingService(PricingServiceProperties pricingProperties,
			ObjectMapper objectMapper) {
		this.pricingProperties = pricingProperties;
		this.objectMapper = objectMapper;
	}

	public Map<String, Object> estimate(ManagerPricingEstimateRequest request, Authentication authentication) {
		if (!pricingProperties.ready()) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Pricing service chua duoc cau hinh tren backend.");
		}
		long userId = requireUserId(authentication);
		String role = isAdmin(authentication) ? "ADMIN" : "MANAGER";

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("requestId", "mgr_" + UUID.randomUUID().toString().replace("-", ""));
		payload.put("requestedBy", Map.of(
				"userId", userId,
				"username", authentication != null ? authentication.getName() : null,
				"role", role,
				"branchId", request.getBranchId()));
		payload.put("vehicleInput", buildVehicleInput(request));
		payload.put("imageAssets", buildImageAssets(request.getImageAssets()));

		try {
			String payloadJson = objectMapper.writeValueAsString(payload);
			HttpURLConnection connection = openJsonPostConnection(payloadJson);
			int statusCode = connection.getResponseCode();
			String responseBody = readConnectionBody(connection, statusCode);
			if (statusCode >= 200 && statusCode < 300) {
				if (responseBody == null || responseBody.isBlank()) {
					return Map.of();
				}
				return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
			}
			String message = extractRemoteMessage(responseBody);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
					"Pricing service loi: " + (message == null || message.isBlank() ? ("HTTP " + statusCode) : message));
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
					"Khong goi duoc pricing service: " + ex.getMessage());
		}
	}

	private static Map<String, Object> buildVehicleInput(ManagerPricingEstimateRequest request) {
		var input = request.getVehicleInput();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", input.getTitle());
		payload.put("categoryId", input.getCategoryId());
		payload.put("subcategoryId", input.getSubcategoryId());
		payload.put("year", input.getYear());
		payload.put("mileage", input.getMileage());
		payload.put("fuel", input.getFuel());
		payload.put("transmission", input.getTransmission());
		payload.put("bodyStyle", input.getBodyStyle());
		payload.put("origin", input.getOrigin());
		payload.put("description", input.getDescription());
		return payload;
	}

	private static List<Map<String, Object>> buildImageAssets(List<ManagerPricingImageAssetRequest> items) {
		return items.stream()
				.map(item -> {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("url", item.getUrl());
					row.put("publicId", nonBlank(item.getPublicId()) ? item.getPublicId() : deriveCloudinaryPublicId(item.getUrl()));
					row.put("source", nonBlank(item.getSource()) ? item.getSource() : "cloudinary");
					row.put("declaredGroup", item.getDeclaredGroup());
					row.put("caption", item.getCaption());
					row.put("captionBy", item.getCaptionBy());
					row.put("captionType", item.getCaptionType());
					return row;
				})
				.toList();
	}

	private String extractRemoteMessage(String body) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
			Object message = parsed.get("message");
			if (message instanceof String msg && !msg.isBlank()) {
				return msg;
			}
			Object detail = parsed.get("detail");
			if (detail instanceof String msg && !msg.isBlank()) {
				return msg;
			}
			Object error = parsed.get("error");
			if (error instanceof String msg && !msg.isBlank()) {
				return msg;
			}
		} catch (Exception ignored) {
		}
		return body.length() > 220 ? body.substring(0, 220) : body;
	}

	private HttpURLConnection openJsonPostConnection(String payloadJson) throws Exception {
		URL url = new URL(joinUrl(pricingProperties.getBaseUrl(), pricingProperties.getEstimatePath()));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setConnectTimeout(pricingProperties.getConnectTimeoutMs());
		connection.setReadTimeout(pricingProperties.getReadTimeoutMs());
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setUseCaches(false);
		connection.setRequestProperty("Authorization", "Bearer " + pricingProperties.getInternalToken());
		connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Connection", "close");
		byte[] bodyBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
		connection.setFixedLengthStreamingMode(bodyBytes.length);
		try (OutputStream outputStream = connection.getOutputStream()) {
			outputStream.write(bodyBytes);
			outputStream.flush();
		}
		return connection;
	}

	private static String readConnectionBody(HttpURLConnection connection, int statusCode) throws Exception {
		InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
		if (stream == null) {
			return "";
		}
		try (InputStream inputStream = stream) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			connection.disconnect();
		}
	}

	private static String joinUrl(String baseUrl, String path) {
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String suffix = path.startsWith("/") ? path : "/" + path;
		return base + suffix;
	}

	private static boolean nonBlank(String value) {
		return value != null && !value.isBlank();
	}

	private static String deriveCloudinaryPublicId(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		int uploadIdx = url.indexOf("/upload/");
		if (uploadIdx < 0) {
			return null;
		}
		String afterUpload = url.substring(uploadIdx + "/upload/".length());
		String[] parts = afterUpload.split("/");
		int start = 0;
		if (parts.length > 0 && parts[0].matches("v\\d+")) {
			start = 1;
		}
		StringBuilder builder = new StringBuilder();
		for (int i = start; i < parts.length; i++) {
			if (parts[i].isBlank()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append('/');
			}
			builder.append(parts[i]);
		}
		String joined = builder.toString();
		int dot = joined.lastIndexOf('.');
		return dot > 0 ? joined.substring(0, dot) : joined;
	}

	private static long requireUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yeu cau dang nhap.");
		}
		return userId;
	}

	private static boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
