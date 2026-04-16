package scu.dn.used_cars_backend.service.payment;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.config.AppSecurityProperties;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Kiểm tra IP gọi webhook thanh toán với whitelist CIDR/IP trong app.security.webhook.
 * Danh sách rỗng → luôn cho phép (tương thích triển khai cũ).
 */
@Service
@RequiredArgsConstructor
public class WebhookIpAllowlistService {

	private static final Logger log = LoggerFactory.getLogger(WebhookIpAllowlistService.class);

	public enum WebhookKind {
		VNPAY, ZALOPAY
	}

	private final AppSecurityProperties appSecurityProperties;

	public boolean isAllowed(HttpServletRequest request, WebhookKind kind) {
		List<String> rules = switch (kind) {
			case VNPAY -> appSecurityProperties.getWebhook().getVnpayAllowCidrs();
			case ZALOPAY -> appSecurityProperties.getWebhook().getZalopayAllowCidrs();
		};
		if (rules == null || rules.isEmpty()) {
			return true;
		}
		String clientIp = HttpServletClientIp.resolve(request);
		for (String rule : rules) {
			if (rule == null || rule.isBlank()) {
				continue;
			}
			if (matchesRule(clientIp, rule.trim())) {
				return true;
			}
		}
		log.warn("Webhook IP không nằm trong whitelist (kind={}, ip={})", kind, clientIp);
		return false;
	}

	private static boolean matchesRule(String clientIp, String rule) {
		try {
			if (!rule.contains("/")) {
				return clientIp.equalsIgnoreCase(rule);
			}
			return ipv4CidrContains(clientIp, rule);
		}
		catch (UnknownHostException e) {
			log.debug("Không parse được rule/IP webhook: rule={} ip={}", rule, clientIp);
			return false;
		}
	}

	private static boolean ipv4CidrContains(String ipStr, String cidr) throws UnknownHostException {
		InetAddress ipAddr = InetAddress.getByName(ipStr);
		if (!(ipAddr instanceof Inet4Address)) {
			return false;
		}
		String[] parts = cidr.split("/");
		if (parts.length != 2) {
			return false;
		}
		InetAddress netAddr = InetAddress.getByName(parts[0].trim());
		if (!(netAddr instanceof Inet4Address)) {
			return false;
		}
		int prefix = Integer.parseInt(parts[1].trim());
		if (prefix < 0 || prefix > 32) {
			return false;
		}
		int ip = ipv4ToInt((Inet4Address) ipAddr);
		int net = ipv4ToInt((Inet4Address) netAddr);
		int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
		return (ip & mask) == (net & mask);
	}

	private static int ipv4ToInt(Inet4Address a) {
		byte[] b = a.getAddress();
		return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
	}
}
