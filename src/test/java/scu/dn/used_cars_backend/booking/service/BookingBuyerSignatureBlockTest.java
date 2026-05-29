package scu.dn.used_cars_backend.booking.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import scu.dn.used_cars_backend.service.installment.InstallmentSignatureImageSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BookingBuyerSignatureBlockTest {

	private static final String TEMPLATE_PATH = "templates/booking/mau-hd-lai-thu-xe.docx";

	@Test
	void template_hasSignatureTableWithBuyerPlaceholders() throws Exception {
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			assertThat(document.getTables()).isNotEmpty();
			XWPFTableCell buyerCell = InstallmentSignatureImageSupport.findBuyerSignatureCell(document);
			assertThat(buyerCell).isNotNull();
			String cellText = buyerCell.getText();
			assertThat(cellText).contains("BÊN B");
			assertThat(cellText).contains("{{BUYER_SIGNATURE_IMAGE}}");
			assertThat(cellText).contains("{{BUYER_PRINTED_NAME}}");
		}
	}

	@Test
	void embedBuyerSignatureBlock_fillsNameInRightTableCellCentered() throws Exception {
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			InstallmentSignatureImageSupport.embedBuyerSignatureBlock(document, null, "Kiki Dang");

			XWPFTableCell buyerCell = InstallmentSignatureImageSupport.findBuyerSignatureCell(document);
			assertThat(buyerCell).isNotNull();

			XWPFParagraph nameParagraph = buyerCell.getParagraphs().stream()
					.filter(p -> "Kiki Dang".equals(p.getText().trim()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Buyer name paragraph not found in right cell"));

			assertThat(nameParagraph.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
			assertThat(nameParagraph.getIndentationLeft()).isZero();
			assertThat(nameParagraph.getIndentationRight()).isZero();
			assertThat(buyerCell.getText()).doesNotContain("{{BUYER_PRINTED_NAME}}");
		}
	}
}
