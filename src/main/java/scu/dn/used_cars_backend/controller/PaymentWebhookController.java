package scu.dn.used_cars_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
public class PaymentWebhookController {

	@GetMapping("/vnpay")
	public Map<String, String> vnpayStub(HttpServletRequest request) {
		Map<String, String> params = new LinkedHashMap<>();
		Enumeration<String> names = request.getParameterNames();
		while (names.hasMoreElements()) {
			String n = names.nextElement();
			params.put(n, request.getParameter(n));
		}
		log.info("webhook vnpay GET params={}", params);
		return Map.of("RspCode", "00");
	}

	@PostMapping("/zalopay")
	public Map<String, Object> zaloStub(@RequestBody(required = false) String body) {
		log.info("webhook zalopay POST body={}", body != null ? body : "");
		return Map.of("return_code", 1);
	}
}
