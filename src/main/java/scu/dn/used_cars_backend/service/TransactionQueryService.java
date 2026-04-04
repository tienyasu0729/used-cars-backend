package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.sales.TransactionRowDto;
import scu.dn.used_cars_backend.entity.FinancialTransaction;
import scu.dn.used_cars_backend.repository.FinancialTransactionRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

	private static final String ROLE_CUSTOMER = "CUSTOMER";
	private static final String ROLE_ADMIN = "ADMIN";
	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final FinancialTransactionRepository financialTransactionRepository;
	private final StaffService staffService;

	@Transactional(readOnly = true)
	public List<TransactionRowDto> page(long actorUserId, String jwtRole, String type, String fromDate, String toDate,
			int page, int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		Instant from = parseStart(fromDate);
		Instant toEx = parseEndExclusive(toDate);
		String t = blankToNull(type);
		Page<FinancialTransaction> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> financialTransactionRepository.pageForUser(actorUserId, t, from, toEx, pr);
			case ROLE_ADMIN -> financialTransactionRepository.pageAll(t, from, toEx, pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield financialTransactionRepository.pageForBranch(bid, t, from, toEx, pr);
			}
		};
		return pg.getContent().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public Map<String, Object> pageMeta(long actorUserId, String jwtRole, String type, String fromDate, String toDate,
			int page, int size) {
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		Instant from = parseStart(fromDate);
		Instant toEx = parseEndExclusive(toDate);
		String t = blankToNull(type);
		Page<FinancialTransaction> pg = switch (jwtRole) {
			case ROLE_CUSTOMER -> financialTransactionRepository.pageForUser(actorUserId, t, from, toEx, pr);
			case ROLE_ADMIN -> financialTransactionRepository.pageAll(t, from, toEx, pr);
			default -> {
				int bid = staffService.getManagerBranchId(actorUserId);
				yield financialTransactionRepository.pageForBranch(bid, t, from, toEx, pr);
			}
		};
		Map<String, Object> m = new HashMap<>();
		m.put("totalElements", pg.getTotalElements());
		m.put("totalPages", pg.getTotalPages());
		m.put("page", pg.getNumber());
		m.put("size", pg.getSize());
		return m;
	}

	private TransactionRowDto toDto(FinancialTransaction t) {
		return TransactionRowDto.builder()
				.id(t.getId())
				.type(t.getType())
				.amount(t.getAmount().toPlainString())
				.description(t.getDescription())
				.status(t.getStatus())
				.paymentGateway(t.getPaymentGateway())
				.referenceType(t.getReferenceType())
				.referenceId(t.getReferenceId())
				.createdAt(t.getCreatedAt().toString())
				.build();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static Instant parseStart(String fromDate) {
		if (fromDate == null || fromDate.isBlank()) {
			return null;
		}
		try {
			LocalDate d = LocalDate.parse(fromDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
			return d.atStartOfDay(VN).toInstant();
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate không hợp lệ.");
		}
	}

	private static Instant parseEndExclusive(String toDate) {
		if (toDate == null || toDate.isBlank()) {
			return null;
		}
		try {
			LocalDate d = LocalDate.parse(toDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1);
			return d.atStartOfDay(VN).toInstant();
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "toDate không hợp lệ.");
		}
	}
}
