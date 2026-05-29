package scu.dn.used_cars_backend.booking.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import scu.dn.used_cars_backend.service.installment.InstallmentSignatureImageSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BookingContractTemplateLoadTest {

	private static final String TEMPLATE_PATH = "templates/booking/mau-hd-lai-thu-xe.docx";

	@Test
	void classpathTemplate_isValidOoxmlZipAndLoadsWithPoi() throws Exception {
		byte[] header;
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
			header = in.readNBytes(4);
		}
		assertThat(header).hasSize(4);
		assertThat(header[0]).isEqualTo((byte) 0x50);
		assertThat(header[1]).isEqualTo((byte) 0x4b);

		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			String text = document.getParagraphs().stream()
					.map(p -> p.getText() != null ? p.getText() : "")
					.reduce("", String::concat);
			assertThat(text).contains("HỢP ĐỒNG LÁI THỬ XE");
			assertThat(text).contains("{{CONTRACT_NO}}");
			assertThat(text).contains("{{TERMS_BODY}}");
			assertThat(text).doesNotContain("Mã xác thực nội dung");
			assertThat(document.getTables()).isNotEmpty();
			assertThat(InstallmentSignatureImageSupport.findBuyerSignatureCell(document)).isNotNull();
		}
	}
}
