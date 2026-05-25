package scu.dn.used_cars_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanContractService {

	private final InstallmentApplicationRepository applicationRepository;

	private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
			.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

	public byte[] generateContractPdf(Long applicationId) {
		InstallmentApplication app = applicationRepository.findByIdWithVehicleAndDocuments(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
			PdfWriter.getInstance(doc, baos);
			doc.open();

			Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(26, 60, 110));
			Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(51, 51, 51));
			Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
			Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);

			Paragraph title = new Paragraph("HOP DONG VAY MUA XE TRA GOP", titleFont);
			title.setAlignment(Element.ALIGN_CENTER);
			title.setSpacingAfter(20);
			doc.add(title);

			Paragraph subTitle = new Paragraph("LOAN CONTRACT", new Font(Font.HELVETICA, 12, Font.NORMAL, Color.GRAY));
			subTitle.setAlignment(Element.ALIGN_CENTER);
			subTitle.setSpacingAfter(30);
			doc.add(subTitle);

			// System info
			doc.add(createSectionHeader("THONG TIN HE THONG", headerFont));
			PdfPTable sysTable = createInfoTable(normalFont, labelFont);
			addRow(sysTable, "Ma ho so", "#" + app.getId(), labelFont, normalFont);
			addRow(sysTable, "Ngay tao", formatDate(app.getCreatedAt()), labelFont, normalFont);
			addRow(sysTable, "Trang thai", app.getStatus().name(), labelFont, normalFont);
			doc.add(sysTable);
			doc.add(Chunk.NEWLINE);

			// Customer info
			doc.add(createSectionHeader("THONG TIN KHACH HANG", headerFont));
			PdfPTable custTable = createInfoTable(normalFont, labelFont);
			addRow(custTable, "Ho ten", safe(app.getFullName()), labelFont, normalFont);
			addRow(custTable, "CCCD/CMND", safe(app.getIdentityNumber()), labelFont, normalFont);
			addRow(custTable, "So dien thoai", safe(app.getPhoneNumber()), labelFont, normalFont);
			addRow(custTable, "Email", safe(app.getEmail()), labelFont, normalFont);
			addRow(custTable, "Ngay sinh", safe(app.getDob()), labelFont, normalFont);
			addRow(custTable, "Dia chi thuong tru", safe(app.getPermanentAddress()), labelFont, normalFont);
			addRow(custTable, "Dia chi hien tai", safe(app.getCurrentAddress()), labelFont, normalFont);
			doc.add(custTable);
			doc.add(Chunk.NEWLINE);

			// Loan info
			doc.add(createSectionHeader("THONG TIN KHOAN VAY", headerFont));
			PdfPTable loanTable = createInfoTable(normalFont, labelFont);
			String vehicleTitle = app.getVehicle() != null ? app.getVehicle().getTitle() : "";
			addRow(loanTable, "Xe", vehicleTitle, labelFont, normalFont);
			addRow(loanTable, "Gia xe", formatMoney(app.getVehiclePrice()), labelFont, normalFont);
			addRow(loanTable, "Tra truoc", formatMoney(app.getPrepaymentAmount()) + " (" + safe(app.getPrepaymentPercent()) + "%)", labelFont, normalFont);
			addRow(loanTable, "So tien vay", formatMoney(app.getLoanAmount()), labelFont, normalFont);
			addRow(loanTable, "Ky han", app.getLoanTermMonths() != null ? app.getLoanTermMonths() + " thang" : "", labelFont, normalFont);
			addRow(loanTable, "Phuong thuc tra", safe(app.getRepaymentMethod()), labelFont, normalFont);

			BigDecimal monthlyPayment = calculateMonthlyPayment(app);
			addRow(loanTable, "Du kien tra hang thang", formatMoney(monthlyPayment), labelFont, normalFont);
			doc.add(loanTable);
			doc.add(Chunk.NEWLINE);

			// Employment info
			doc.add(createSectionHeader("THONG TIN NGHE NGHIEP", headerFont));
			PdfPTable empTable = createInfoTable(normalFont, labelFont);
			addRow(empTable, "Loai hinh", safe(app.getEmploymentType()), labelFont, normalFont);
			addRow(empTable, "Cong ty", safe(app.getCompanyName()), labelFont, normalFont);
			addRow(empTable, "Chuc vu", safe(app.getJobTitle()), labelFont, normalFont);
			addRow(empTable, "Thu nhap/thang", formatMoney(app.getMonthlyIncome()), labelFont, normalFont);
			addRow(empTable, "Chi phi/thang", formatMoney(app.getMonthlyExpenses()), labelFont, normalFont);
			doc.add(empTable);
			doc.add(Chunk.NEWLINE);

			// Signature
			doc.add(createSectionHeader("CHU KY", headerFont));
			if (app.getSignatureUrl() != null && !app.getSignatureUrl().isBlank()) {
				Image signature = loadSignatureImage(app.getSignatureUrl());
				if (signature != null) {
					signature.scaleToFit(200, 80);
					signature.setSpacingAfter(8);
					doc.add(signature);
				}
				doc.add(new Paragraph("Chu ky dien tu da duoc ghi nhan.", normalFont));
				doc.add(new Paragraph("Ngay ky: " + safe(app.getSignedDate()), normalFont));
			} else {
				doc.add(new Paragraph("[Chua co chu ky dien tu - Can ky truc tiep]", normalFont));
			}

			doc.close();
			return baos.toByteArray();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error generating contract PDF for app #{}: {}", applicationId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi tao file PDF hop dong.");
		}
	}

	public byte[] generateIdentityDocsZip(Long applicationId) {
		InstallmentApplication app = applicationRepository.findByIdWithVehicleAndDocuments(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 ZipOutputStream zos = new ZipOutputStream(baos)) {

			List<InstallmentDocument> docs = app.getDocuments();
			if (docs != null) {
				for (InstallmentDocument d : docs) {
					String type = d.getDocumentType() != null ? d.getDocumentType().toLowerCase() : "";
					String folder = resolveDocFolder(type);
					String fileName = folder + "/" + resolveFileName(d);
					addUrlToZip(zos, fileName, d.getDocumentUrl());
				}
			}

			zos.finish();
			return baos.toByteArray();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error generating identity docs ZIP for app #{}: {}", applicationId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi tao file ZIP giay to.");
		}
	}

	public byte[] generateFullPackageZip(Long applicationId) {
		InstallmentApplication app = applicationRepository.findByIdWithVehicleAndDocuments(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 ZipOutputStream zos = new ZipOutputStream(baos)) {

			// 1. Contract PDF
			byte[] contractPdf = generateContractPdf(applicationId);
			zos.putNextEntry(new ZipEntry("loan_contract.pdf"));
			zos.write(contractPdf);
			zos.closeEntry();

			// 2. Identity docs & attachments
			List<InstallmentDocument> docs = app.getDocuments();
			if (docs != null) {
				for (InstallmentDocument d : docs) {
					String type = d.getDocumentType() != null ? d.getDocumentType().toLowerCase() : "";
					String folder = resolveDocFolder(type);
					String fileName = folder + "/" + resolveFileName(d);
					addUrlToZip(zos, fileName, d.getDocumentUrl());
				}
			}

			// 3. Signature
			if (app.getSignatureUrl() != null && !app.getSignatureUrl().isBlank()) {
				addUrlToZip(zos, "signature/signature.png", app.getSignatureUrl());
			}

			zos.finish();
			return baos.toByteArray();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error generating full package ZIP for app #{}: {}", applicationId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi tao file ZIP tron goi ho so.");
		}
	}

	private String resolveDocFolder(String type) {
		if (type.contains("cccd") || type.contains("cmnd") || type.contains("identity") || type.contains("selfie") || type.contains("portrait")) {
			return "identity_docs";
		}
		if (type.contains("deposit") || type.contains("receipt")) {
			return "attachments";
		}
		return "extra_docs";
	}

	private String resolveFileName(InstallmentDocument d) {
		if (d.getOriginalFileName() != null && !d.getOriginalFileName().isBlank()) {
			return d.getOriginalFileName();
		}
		String type = d.getDocumentType() != null ? d.getDocumentType() : "document";
		return type.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + d.getId() + ".jpg";
	}

	private Image loadSignatureImage(String signatureUrl) {
		if (signatureUrl == null || signatureUrl.isBlank()) return null;
		try {
			if (signatureUrl.startsWith("data:")) {
				int comma = signatureUrl.indexOf(',');
				if (comma < 0) return null;
				byte[] bytes = Base64.getDecoder().decode(signatureUrl.substring(comma + 1));
				return Image.getInstance(bytes);
			}
			try (InputStream is = URI.create(signatureUrl).toURL().openStream()) {
				return Image.getInstance(is.readAllBytes());
			}
		} catch (Exception e) {
			log.warn("Cannot load signature image: {}", e.getMessage());
			return null;
		}
	}

	private void addUrlToZip(ZipOutputStream zos, String entryName, String url) {
		if (url == null || url.isBlank()) return;
		try {
			URI uri = URI.create(url);
			try (InputStream is = uri.toURL().openStream()) {
				zos.putNextEntry(new ZipEntry(entryName));
				is.transferTo(zos);
				zos.closeEntry();
			}
		} catch (Exception e) {
			log.warn("Cannot download file for ZIP entry '{}': {}", entryName, e.getMessage());
		}
	}

	private BigDecimal calculateMonthlyPayment(InstallmentApplication app) {
		if (app.getLoanAmount() == null || app.getLoanTermMonths() == null || app.getLoanTermMonths() == 0) {
			return BigDecimal.ZERO;
		}
		// Simple equal principal + interest calculation with assumed 8% if no config
		BigDecimal principal = app.getLoanAmount();
		int months = app.getLoanTermMonths();
		BigDecimal monthlyRate = new BigDecimal("8.0").divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
		// PMT formula: P * r * (1+r)^n / ((1+r)^n - 1)
		BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
		double pow = Math.pow(onePlusR.doubleValue(), months);
		BigDecimal numerator = principal.multiply(monthlyRate).multiply(BigDecimal.valueOf(pow));
		BigDecimal denominator = BigDecimal.valueOf(pow - 1);
		if (denominator.compareTo(BigDecimal.ZERO) == 0) return principal.divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP);
		return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
	}

	private Paragraph createSectionHeader(String text, Font font) {
		Paragraph p = new Paragraph(text, font);
		p.setSpacingBefore(10);
		p.setSpacingAfter(8);
		return p;
	}

	private PdfPTable createInfoTable(Font normalFont, Font labelFont) {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		try {
			table.setWidths(new float[]{35f, 65f});
		} catch (DocumentException ignored) {
		}
		return table;
	}

	private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
		PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
		labelCell.setBorder(0);
		labelCell.setPadding(4);
		table.addCell(labelCell);

		PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
		valueCell.setBorder(0);
		valueCell.setPadding(4);
		table.addCell(valueCell);
	}

	private String formatMoney(BigDecimal amount) {
		if (amount == null) return "-";
		return String.format("%,.0f VND", amount);
	}

	private String formatDate(Instant instant) {
		if (instant == null) return "-";
		return VN_DATE.format(instant);
	}

	private String safe(Object o) {
		return o != null ? o.toString() : "-";
	}
}
