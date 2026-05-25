package scu.dn.used_cars_backend.service.installment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentContractPdfService {

	private final InstallmentContractDocxService docxService;

	public byte[] generateContractPdf(Long applicationId) {
		byte[] docx = docxService.generateContractDocx(applicationId);
		return InstallmentContractPdfConverter.convertDocxToPdf(docx);
	}
}
