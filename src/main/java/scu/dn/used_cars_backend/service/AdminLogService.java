package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.admin.AdminLogPageResult;
import scu.dn.used_cars_backend.dto.admin.AdminLogRowDto;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.entity.AuditLog;
import scu.dn.used_cars_backend.repository.AuditLogRepository;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminLogService {

	private final AuditLogRepository auditLogRepository;

	@Transactional(readOnly = true)
	public AdminLogPageResult page(String module, Long userId, String fromDate, String toDate, String actionSearch,
			int page, int size) {
		Instant fromTs = parseInstant(fromDate);
		Instant toTs = parseInstant(toDate);
		String mod = module != null && !module.isBlank() ? module.trim() : null;
		String act = actionSearch != null && !actionSearch.isBlank() ? actionSearch.trim() : null;
		int sz = Math.max(1, Math.min(size, 200));
		int pgIdx = Math.max(0, page);
		Pageable p = PageRequest.of(pgIdx, sz, Sort.by(Sort.Direction.DESC, "timestamp"));
		Page<AuditLog> result = auditLogRepository.search(mod, userId, fromTs, toTs, act, p);
		List<AdminLogRowDto> rows = new ArrayList<>();
		for (AuditLog a : result.getContent()) {
			String ts = a.getTimestamp() != null ? DateTimeFormatter.ISO_INSTANT.format(a.getTimestamp()) : "";
			String u = a.getUserName() != null && !a.getUserName().isBlank() ? a.getUserName() : "—";
			rows.add(AdminLogRowDto.builder()
					.id(String.valueOf(a.getId()))
					.user(u)
					.action(a.getAction())
					.module(a.getModule())
					.timestamp(ts)
					.build());
		}
		PageMetaDto meta = PageMetaDto.builder()
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.build();
		return AdminLogPageResult.builder().content(rows).meta(meta).build();
	}

	private static Instant parseInstant(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(s.trim());
		} catch (DateTimeParseException ex) {
			return null;
		}
	}
}
