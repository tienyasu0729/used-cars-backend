package scu.dn.used_cars_backend.service.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class VnpayFormEncoding {

	private VnpayFormEncoding() {
	}

	public static String encodeValue(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String enc = URLEncoder.encode(value, StandardCharsets.UTF_8);
		enc = enc.replace("+", "%20");
		enc = enc.replace("%21", "!");
		enc = enc.replace("%27", "'");
		enc = enc.replace("%28", "(");
		enc = enc.replace("%29", ")");
		enc = enc.replace("%7E", "~");
		enc = enc.replace("%2A", "*");
		return enc.replace("%20", "+");
	}
}
