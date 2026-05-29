package scu.dn.used_cars_backend.booking.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import scu.dn.used_cars_backend.service.installment.InstallmentContractPdfConverter;
import scu.dn.used_cars_backend.service.installment.InstallmentSignatureImageSupport;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BookingContractPdfGenerationTest {

	private static final String TEMPLATE_PATH = "templates/booking/mau-hd-lai-thu-xe.docx";

	@Test
	void convertsBookingTemplateDocxToValidPdf() throws Exception {
		byte[] docx;
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 var doc = new XWPFDocument(in);
			 var out = new ByteArrayOutputStream()) {
			InstallmentSignatureImageSupport.embedBuyerSignatureBlock(doc, null, "Kiki Dang");
			doc.write(out);
			docx = out.toByteArray();
		}

		byte[] pdf = InstallmentContractPdfConverter.convertDocxToPdf(docx);

		assertThat(pdf.length).isGreaterThan(2_000);
		assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
	}
}
