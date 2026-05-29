package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.BookingContract;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.service.installment.InstallmentContractPdfConverter;
import scu.dn.used_cars_backend.service.installment.InstallmentSignatureImageSupport;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingContractDocxService {

	private static final String TEMPLATE_PATH = "templates/booking/mau-hd-lai-thu-xe.docx";
	private static final String TERMS_BODY_PLACEHOLDER = "{{TERMS_BODY}}";

	private final BookingContractFieldMapper fieldMapper;

	public byte[] generateContractPdf(Booking booking, BookingContract contract, User customer, String termsBody) {
		byte[] docx = generateContractDocx(booking, contract, customer, termsBody);
		return InstallmentContractPdfConverter.convertDocxToPdf(docx);
	}

	public byte[] generateContractDocx(Booking booking, BookingContract contract, User customer, String termsBody) {
		Map<String, String> fields = fieldMapper.buildFields(booking, contract, customer, termsBody);

		try (InputStream templateStream = new ClassPathResource(TEMPLATE_PATH).getInputStream();
			 XWPFDocument document = new XWPFDocument(templateStream);
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			replaceInParagraphs(document.getParagraphs(), fields);
			for (XWPFTable table : document.getTables()) {
				for (XWPFTableRow row : table.getRows()) {
					for (XWPFTableCell cell : row.getTableCells()) {
						replaceInParagraphs(cell.getParagraphs(), fields);
					}
				}
			}

			expandTermsBodyPlaceholder(document, termsBody);

			String printedName = BookingContractFieldMapper.resolveCustomerSignatureName(contract, customer);
			String imageUrl = BookingContractFieldMapper.resolveCustomerSignatureImageUrl(contract);
			InstallmentSignatureImageSupport.embedBuyerSignatureBlock(document, imageUrl, printedName);

			document.write(out);
			return out.toByteArray();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error generating booking contract DOCX for booking #{}: {}", booking.getId(), e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi tao file Word hop dong lai thu.");
		}
	}

	private static void expandTermsBodyPlaceholder(XWPFDocument document, String termsBody) {
		XWPFParagraph holder = null;
		for (XWPFParagraph paragraph : document.getParagraphs()) {
			String text = paragraph.getText();
			if (text != null && text.contains(TERMS_BODY_PLACEHOLDER)) {
				holder = paragraph;
				break;
			}
		}
		if (holder == null) {
			return;
		}

		List<String> lines = Arrays.stream((termsBody != null ? termsBody : "").split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.filter(line -> !"HỢP ĐỒNG LÁI THỬ XE".equalsIgnoreCase(line))
				.toList();

		if (lines.isEmpty()) {
			setParagraphText(holder, "");
			return;
		}

		setParagraphText(holder, lines.get(0));
		XmlCursor cursor = holder.getCTP().newCursor();
		cursor.toNextSibling();
		for (int i = 1; i < lines.size(); i++) {
			XWPFParagraph next = document.insertNewParagraph(cursor);
			copyParagraphStyle(holder, next);
			setParagraphText(next, lines.get(i));
			cursor = next.getCTP().newCursor();
			cursor.toNextSibling();
		}
	}

	private static void copyParagraphStyle(XWPFParagraph source, XWPFParagraph target) {
		if (source.getCTP().isSetPPr()) {
			target.getCTP().setPPr((CTPPr) source.getCTP().getPPr().copy());
		}
	}

	private static void setParagraphText(XWPFParagraph paragraph, String text) {
		CTRPr style = captureRunStyle(paragraph);
		for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
			paragraph.removeRun(i);
		}
		XWPFRun run = paragraph.createRun();
		if (style != null) {
			run.getCTR().setRPr(style);
		}
		run.setText(text, 0);
	}

	private static CTRPr captureRunStyle(XWPFParagraph paragraph) {
		if (paragraph.getRuns().isEmpty()) {
			return null;
		}
		CTRPr rPr = paragraph.getRuns().get(0).getCTR().getRPr();
		return rPr != null ? (CTRPr) rPr.copy() : null;
	}

	private static void replaceInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> fields) {
		for (XWPFParagraph paragraph : paragraphs) {
			replaceInParagraph(paragraph, fields);
		}
	}

	private static void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> fields) {
		List<XWPFRun> runs = paragraph.getRuns();
		if (runs.isEmpty()) {
			String updated = applyPlaceholders("", fields);
			if (!updated.isEmpty()) {
				paragraph.createRun().setText(updated);
			}
			return;
		}

		StringBuilder merged = new StringBuilder();
		for (XWPFRun run : runs) {
			String part = run.getText(0);
			if (part != null) {
				merged.append(part);
			}
		}
		String text = merged.toString();
		if (text.isEmpty() || text.contains(TERMS_BODY_PLACEHOLDER)) {
			return;
		}

		String updated = applyPlaceholders(text, fields);
		if (updated.equals(text)) {
			return;
		}
		for (int i = runs.size() - 1; i > 0; i--) {
			paragraph.removeRun(i);
		}
		paragraph.getRuns().get(0).setText(updated, 0);
	}

	private static String applyPlaceholders(String text, Map<String, String> fields) {
		String updated = text;
		for (Map.Entry<String, String> entry : fields.entrySet()) {
			if ("TERMS_BODY".equals(entry.getKey())) {
				continue;
			}
			String token = "{{" + entry.getKey() + "}}";
			String value = entry.getValue() != null ? entry.getValue() : "";
			updated = updated.replace(token, value);
		}
		return updated;
	}
}
