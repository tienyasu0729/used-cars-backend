package scu.dn.used_cars_backend.service.installment;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentBuyerSignatureBlockTest {

	private static final String TEMPLATE_PATH = "templates/installment/mau-hd-tra-gop-mua-ban-hh.docx";

	@Test
	void resolveBuyerDisplayName_prefersFullNameThenCustomer() {
		InstallmentApplication app = new InstallmentApplication();
		app.setFullName("Tran Thi B");
		assertThat(InstallmentContractFieldMapper.resolveBuyerDisplayName(app)).isEqualTo("Tran Thi B");

		app.setFullName(null);
		User customer = new User();
		customer.setName("Khach Hang C");
		app.setCustomer(customer);
		assertThat(InstallmentContractFieldMapper.resolveBuyerDisplayName(app)).isEqualTo("Khach Hang C");
	}

	@Test
	void template_hasSignatureTableWithBuyerPlaceholders() throws Exception {
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			assertThat(document.getTables()).isNotEmpty();
			XWPFTableCell buyerCell = InstallmentSignatureImageSupport.findBuyerSignatureCell(document);
			assertThat(buyerCell).isNotNull();
			String cellText = buyerCell.getText();
			assertThat(cellText).contains("BÊN B");
			assertThat(cellText).contains(InstallmentSignatureImageSupport.PLACEHOLDER_SIGNATURE_IMAGE);
			assertThat(cellText).contains(InstallmentSignatureImageSupport.PLACEHOLDER_PRINTED_NAME);
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
			assertThat(buyerCell.getText()).doesNotContain(InstallmentSignatureImageSupport.PLACEHOLDER_PRINTED_NAME);
		}
	}

	@Test
	void embedBuyerSignatureBlock_doesNotAddFullWidthParagraphsOutsideTable() throws Exception {
		int paraCountBefore;
		try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(in)) {
			paraCountBefore = document.getParagraphs().size();
			InstallmentSignatureImageSupport.embedBuyerSignatureBlock(document, null, "Kiki Dang");
			assertThat(document.getParagraphs()).hasSize(paraCountBefore);
		}
	}
}
