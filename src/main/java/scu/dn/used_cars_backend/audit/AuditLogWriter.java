package scu.dn.used_cars_backend.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditLogWriter {

	private final AuditLogRepository auditLogRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void persist(Long userId, String userName, String module, String action, String details, String ip) {
		AuditLog row = new AuditLog();
		row.setUserId(userId);
		row.setUserName(trunc(userName, 100));
		row.setModule(trunc(module, 50));
		row.setAction(trunc(action, 100));
		row.setDetails(details != null && details.length() > 4000 ? details.substring(0, 4000) : details);
		row.setIpAddress(trunc(ip, 45));
		auditLogRepository.save(row);
	}

	private static String trunc(String s, int max) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.length() <= max ? t : t.substring(0, max);
	}
}
