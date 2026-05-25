package scu.dn.used_cars_backend.service;

import com.lowagie.text.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.InstallmentDocument;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.service.installment.InstallmentContractDocxService;
import scu.dn.used_cars_backend.service.installment.InstallmentContractPdfService;

import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanContractService {

	private final InstallmentApplicationRepository applicationRepository;
	private final InstallmentContractPdfService contractPdfService;
	private final InstallmentContractDocxService contractDocxService;

	public byte[] generateContractPdf(Long applicationId) {
		return contractPdfService.generateContractPdf(applicationId);
	}

	public byte[] generateContractDocx(Long applicationId) {
		return contractDocxService.generateContractDocx(applicationId);
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

			byte[] contractPdf = generateContractPdf(applicationId);
			zos.putNextEntry(new ZipEntry("hop-dong-tra-gop.pdf"));
			zos.write(contractPdf);
			zos.closeEntry();

			byte[] contractDocx = generateContractDocx(applicationId);
			zos.putNextEntry(new ZipEntry("hop-dong-tra-gop.docx"));
			zos.write(contractDocx);
			zos.closeEntry();

			List<InstallmentDocument> docs = app.getDocuments();
			if (docs != null) {
				for (InstallmentDocument d : docs) {
					String type = d.getDocumentType() != null ? d.getDocumentType().toLowerCase() : "";
					String folder = resolveDocFolder(type);
					String fileName = folder + "/" + resolveFileName(d);
					addUrlToZip(zos, fileName, d.getDocumentUrl());
				}
			}

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
		if (type.contains("cccd") || type.contains("cmnd") || type.contains("identity")
				|| type.contains("selfie") || type.contains("portrait") || type.contains("household")) {
			return "identity_docs";
		}
		if (type.contains("deposit") || type.contains("receipt") || type.contains("income")) {
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

	private void addUrlToZip(ZipOutputStream zos, String entryName, String url) {
		if (url == null || url.isBlank()) {
			return;
		}
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
}
