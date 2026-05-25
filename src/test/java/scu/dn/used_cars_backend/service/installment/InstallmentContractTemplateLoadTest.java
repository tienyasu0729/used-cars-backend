package scu.dn.used_cars_backend.service.installment;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentContractTemplateLoadTest {

	private static final String TEMPLATE_PATH = "templates/installment/mau-hd-tra-gop-mua-ban-hh.docx";

	@Test
	void classpathTemplate_isValidOoxmlZipAndLoadsWithPoi() throws Exception {
		byte[] header;
		try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
			header = in.readNBytes(4);
		}
		assertThat(header).hasSize(4);
		assertThat(header[0]).isEqualTo((byte) 0x50); // P
		assertThat(header[1]).isEqualTo((byte) 0x4b); // K

		try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			String text = document.getParagraphs().stream()
					.map(p -> p.getText() != null ? p.getText() : "")
					.reduce("", String::concat);
			assertThat(text).contains("HỢP ĐỒNG");
			assertThat(text).contains("{{CONTRACT_NO}}");
			assertThat(document.getTables()).isNotEmpty();
			assertThat(InstallmentSignatureImageSupport.findBuyerSignatureCell(document)).isNotNull();
		}
	}
}
