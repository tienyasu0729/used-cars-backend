package scu.dn.used_cars_backend.booking.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.audit.AuditLogWriter;
import scu.dn.used_cars_backend.booking.dto.ActiveContractTermsDto;
import scu.dn.used_cars_backend.booking.dto.CompleteContractRequest;
import scu.dn.used_cars_backend.booking.dto.ContractPreviewResponse;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.BookingContract;
import scu.dn.used_cars_backend.booking.repository.BookingContractRepository;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.EmailNotificationService;
import scu.dn.used_cars_backend.service.MediaUploadContext;
import scu.dn.used_cars_backend.service.StaffService;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingContractService {

	private final BookingRepository bookingRepository;
	private final BookingContractRepository contractRepository;
	private final UserRepository userRepository;
	private final BookingService bookingService;
	private final CloudinaryUploadService cloudinaryUploadService;
	private final ContractTermsService contractTermsService;
	private final EmailNotificationService emailNotificationService;
	private final AuditLogWriter auditLogWriter;
	private final StaffService staffService;

	@Transactional(readOnly = true)
	public ContractPreviewResponse getContractPreview(long bookingId, long customerId) {
		Booking b = loadBookingForCustomer(bookingId, customerId);
		return buildContractPreview(bookingId, b);
	}

	@Transactional(readOnly = true)
	public ContractPreviewResponse getContractPreviewForStaff(long bookingId, long actorUserId, boolean actorIsAdmin) {
		Booking b = loadBookingForStaff(bookingId, actorUserId, actorIsAdmin);
		return buildContractPreview(bookingId, b);
	}

	private ContractPreviewResponse buildContractPreview(long bookingId, Booking b) {
		BookingContract c = contractRepository.findByBooking_Id(bookingId).orElse(null);

		User customer = userRepository.findById(b.getCustomerId()).orElse(null);
		Vehicle v = b.getVehicle();
		ActiveContractTermsDto active = contractTermsService.getActiveTerms();
		String termsVersion;
		String termsContent;
		if (c != null && "SIGNED".equals(c.getContractStatus()) && c.getTermsVersion() != null) {
			termsVersion = c.getTermsVersion();
			termsContent = contractTermsService.getTermsContentByVersionOrFallback(c.getTermsVersion());
		} else {
			termsVersion = active.getVersion();
			termsContent = active.getContent();
		}

		ContractPreviewResponse.ContractPreviewResponseBuilder builder = ContractPreviewResponse.builder()
				.bookingId(bookingId)
				.termsVersion(termsVersion)
				.termsContent(termsContent)
				.customerName(customer != null ? customer.getName() : null)
				.customerPhone(customer != null ? customer.getPhone() : null)
				.customerEmail(customer != null ? customer.getEmail() : null)
				.vehicleTitle(v.getTitle())
				.vehicleListingId(v.getListingId())
				.branchName(b.getBranch().getName())
				.bookingDate(b.getBookingDate())
				.timeSlot(b.getTimeSlot());

		if (c != null) {
			builder.contractStatus(c.getContractStatus())
					.signatureUrl(c.getSignatureUrl())
					.idCardUrl(c.getIdCardUrl())
					.licenseUrl(c.getLicenseUrl())
					.contentSha256(c.getContentSha256())
					.signedAt(c.getSignedAt())
					.expiresAt(c.getExpiresAt());
		} else {
			builder.contractStatus("PENDING_SIGNATURE");
		}
		return builder.build();
	}

	public Map<String, CloudinarySignedUploadDto> getSignatureUploadUrls(long bookingId, long customerId) {
		loadBookingForCustomer(bookingId, customerId);
		return Map.of(
				"signature", cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.CONTRACT_SIGNATURE, bookingId),
				"idCard", cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.CONTRACT_ID_IMAGE, bookingId),
				"license", cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.CONTRACT_LICENSE_IMAGE, bookingId));
	}

	@Transactional(rollbackFor = Exception.class)
	public ContractPreviewResponse completeContract(long bookingId, long customerId, CompleteContractRequest req, String clientIp) {
		Booking b = loadBookingForCustomer(bookingId, customerId);
		if (!"AwaitingContract".equals(b.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Lịch hẹn không ở trạng thái chờ ký hợp đồng.");
		}
		if (!Boolean.TRUE.equals(req.getAgreed())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Bạn phải đồng ý với điều khoản hợp đồng.");
		}

		if (contractRepository.findByBooking_Id(bookingId)
				.filter(c -> "SIGNED".equals(c.getContractStatus())).isPresent()) {
			throw new BusinessException(ErrorCode.CONTRACT_ALREADY_SIGNED, "Hợp đồng đã được ký.");
		}

		validateSignatureUrls(req, bookingId);

		User customer = userRepository.findById(b.getCustomerId()).orElse(null);
		ActiveContractTermsDto terms = contractTermsService.getActiveTerms();
		Instant signedAt = Instant.now();
		String contentHash = computeHash(b, customer, req, signedAt, terms.getVersion());

		BookingContract contract = contractRepository.findByBooking_Id(bookingId)
				.orElseGet(() -> {
					BookingContract nc = new BookingContract();
					nc.setBooking(b);
					return nc;
				});
		contract.setTermsVersion(terms.getVersion());
		contract.setSignatureType(req.getSignatureType());
		contract.setSignatureUrl(req.getSignatureUrl());
		contract.setIdCardUrl(req.getIdCardUrl());
		contract.setLicenseUrl(req.getLicenseUrl());
		contract.setContentSha256(contentHash);
		contract.setSignedAt(signedAt);
		contract.setContractStatus("SIGNED");
		contract.setExpiresAt(null);
		contractRepository.save(contract);

		bookingService.activateBookingAfterContractSigned(bookingId, customerId);

		auditLogWriter.persist(
				customerId,
				customer != null ? customer.getName() : null,
				"Contract",
				"SIGN",
				"bookingId=" + bookingId + ", termsVersion=" + terms.getVersion() + ", contentSha256=" + contentHash,
				clientIp);

		try {
			byte[] pdf = generateContractPdf(bookingId, customerId);
			if (customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()) {
				emailNotificationService.sendTestDriveContractSignedEmailAsync(
						bookingId,
						customer.getEmail().trim(),
						customer.getName(),
						b.getVehicle().getTitle(),
						b.getBookingDate().toString(),
						b.getTimeSlot().toString(),
						pdf);
			}
		} catch (Exception e) {
			log.warn("Gửi email/PDF hậu ký hợp đồng thất bại (bookingId={}): {}", bookingId, e.getMessage());
		}

		return getContractPreview(bookingId, customerId);
	}

	@Transactional(readOnly = true)
	public byte[] generateContractPdf(long bookingId, long customerId) {
		Booking b = loadBookingForCustomer(bookingId, customerId);
		return generateSignedContractPdf(bookingId, b);
	}

	@Transactional(readOnly = true)
	public byte[] generateContractPdfForStaff(long bookingId, long actorUserId, boolean actorIsAdmin) {
		Booking b = loadBookingForStaff(bookingId, actorUserId, actorIsAdmin);
		return generateSignedContractPdf(bookingId, b);
	}

	private byte[] generateSignedContractPdf(long bookingId, Booking b) {
		BookingContract c = contractRepository.findByBooking_Id(bookingId)
				.filter(ct -> "SIGNED".equals(ct.getContractStatus()))
				.orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Hợp đồng chưa được ký."));
		User customer = userRepository.findById(b.getCustomerId()).orElse(null);
		String termsBody = contractTermsService.getTermsContentByVersionOrFallback(c.getTermsVersion());

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Document doc = new Document();
			PdfWriter.getInstance(doc, baos);
			doc.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
			Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

			doc.add(new Paragraph("HOP DONG LAI THU XE (Phien ban: " + c.getTermsVersion() + ")", titleFont));
			doc.add(new Paragraph(" "));
			doc.add(new Paragraph("Khach hang: " + (customer != null ? customer.getName() : "N/A"), boldFont));
			doc.add(new Paragraph("Dien thoai: " + (customer != null ? customer.getPhone() : "N/A"), normalFont));
			doc.add(new Paragraph("Xe: " + b.getVehicle().getTitle(), normalFont));
			doc.add(new Paragraph("Chi nhanh: " + b.getBranch().getName(), normalFont));
			doc.add(new Paragraph("Ngay lai thu: " + b.getBookingDate() + " " + b.getTimeSlot(), normalFont));
			doc.add(new Paragraph(" "));
			doc.add(new Paragraph(termsBody, normalFont));
			doc.add(new Paragraph(" "));
			doc.add(new Paragraph("Loai chu ky: " + c.getSignatureType(), normalFont));
			doc.add(new Paragraph("Hash noi dung: " + c.getContentSha256(), normalFont));
			doc.add(new Paragraph("Thoi diem ky: " + c.getSignedAt(), normalFont));

			doc.close();
			return baos.toByteArray();
		} catch (Exception e) {
			log.error("PDF generation failed for booking {}", bookingId, e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể tạo file PDF.");
		}
	}

	private Booking loadBookingForStaff(long bookingId, long actorUserId, boolean actorIsAdmin) {
		Booking b = bookingRepository.findWithDetailsById(bookingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lịch hẹn."));
		if (!actorIsAdmin) {
			int branchId = staffService.resolveBranchIdForAdminOrBranchStaff(null, actorUserId, false);
			if (b.getBranch() == null || b.getBranch().getId() != branchId) {
				throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Lịch hẹn không thuộc chi nhánh bạn quản lý.");
			}
		}
		return b;
	}

	private Booking loadBookingForCustomer(long bookingId, long customerId) {
		Booking b = bookingRepository.findWithDetailsById(bookingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lịch hẹn."));
		if (!b.getCustomerId().equals(customerId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền xem lịch hẹn này.");
		}
		return b;
	}

	private void validateSignatureUrls(CompleteContractRequest req, long bookingId) {
		if (!cloudinaryUploadService.isUploadConfigured()) return;

		boolean isDrawSignature = "draw".equalsIgnoreCase(req.getSignatureType());
		boolean isDataUrl = req.getSignatureUrl() != null && req.getSignatureUrl().startsWith("data:");
		if (isDrawSignature && isDataUrl) {
			// data URL from canvas — skip Cloudinary validation
		} else if ("type".equalsIgnoreCase(req.getSignatureType())) {
			// plain text name — skip Cloudinary validation
		} else {
			cloudinaryUploadService.assertSecureUrlMatchesSignedContext(
					req.getSignatureUrl(), MediaUploadContext.CONTRACT_SIGNATURE, bookingId);
		}

		validatePipeSeparatedUrls(req.getIdCardUrl(), MediaUploadContext.CONTRACT_ID_IMAGE, bookingId);
		validatePipeSeparatedUrls(req.getLicenseUrl(), MediaUploadContext.CONTRACT_LICENSE_IMAGE, bookingId);
	}

	private void validatePipeSeparatedUrls(String raw, MediaUploadContext context, long bookingId) {
		if (raw == null || raw.isBlank()) return;
		for (String part : raw.split("\\|")) {
			String url = part.trim();
			if (!url.isEmpty()) {
				cloudinaryUploadService.assertSecureUrlMatchesSignedContext(url, context, bookingId);
			}
		}
	}

	private String computeHash(Booking b, User customer, CompleteContractRequest req, Instant signedAt, String termsVersion) {
		try {
			String canonical = String.join("|",
					String.valueOf(b.getId()),
					customer != null ? String.valueOf(customer.getId()) : "",
					customer != null && customer.getName() != null ? customer.getName() : "",
					b.getVehicle().getTitle(),
					b.getBookingDate().toString(),
					b.getTimeSlot().toString(),
					termsVersion != null ? termsVersion : "",
					req.getSignatureType(),
					req.getSignatureUrl(),
					req.getIdCardUrl() != null ? req.getIdCardUrl() : "",
					req.getLicenseUrl() != null ? req.getLicenseUrl() : "",
					signedAt.toString());
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi tạo hash hợp đồng.");
		}
	}
}
