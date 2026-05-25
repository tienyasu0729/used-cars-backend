package scu.dn.used_cars_backend.service.installment;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentContractPdfConverterTest {

	private static final String TEMPLATE_PATH = "templates/installment/mau-hd-tra-gop-mua-ban-hh.docx";

	@Test
	void convertsClasspathTemplateDocxToValidPdf() throws Exception {
		byte[] docx;
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 var doc = new XWPFDocument(in);
			 var out = new ByteArrayOutputStream()) {
			doc.write(out);
			docx = out.toByteArray();
		}

		byte[] pdf = InstallmentContractPdfConverter.convertDocxToPdf(docx);

		assertThat(pdf.length).isGreaterThan(500);
		assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
	}
}
