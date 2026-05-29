package scu.dn.used_cars_backend.service.installment;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;

/**
 * Fills buyer signature placeholders inside the right cell of the contract signature table.
 * Template uses a 2-column table so each column has its own center axis (no page indent hacks).
 */
@Slf4j
public final class InstallmentSignatureImageSupport {

	static final String PLACEHOLDER_SIGNATURE_IMAGE = "{{BUYER_SIGNATURE_IMAGE}}";
	static final String PLACEHOLDER_PRINTED_NAME = "{{BUYER_PRINTED_NAME}}";

	private InstallmentSignatureImageSupport() {
	}

	public static void embedBuyerSignatureBlock(XWPFDocument document, String signatureUrl, String buyerFullName) {
		XWPFTableCell buyerCell = findBuyerSignatureCell(document);
		if (buyerCell == null) {
			log.warn("Buyer signature table cell not found in contract template");
			return;
		}

		String displayName = buyerFullName == null ? "" : buyerFullName.trim();
		boolean hasName = !displayName.isEmpty() && !"—".equals(displayName);

		for (XWPFParagraph paragraph : buyerCell.getParagraphs()) {
			String text = paragraph.getText();
			if (text == null) {
				continue;
			}
			if (text.contains(PLACEHOLDER_SIGNATURE_IMAGE)) {
				fillSignatureImageParagraph(paragraph, signatureUrl);
			} else if (text.contains(PLACEHOLDER_PRINTED_NAME)) {
				fillPrintedNameParagraph(paragraph, hasName ? displayName : "");
			}
		}
	}

	private static void fillSignatureImageParagraph(XWPFParagraph paragraph, String signatureUrl) {
		clearParagraphRuns(paragraph);
		ensureCenterAligned(paragraph);

		if (signatureUrl == null || signatureUrl.isBlank()) {
			return;
		}
		byte[] imageBytes = loadImageBytes(signatureUrl);
		if (imageBytes == null || imageBytes.length == 0) {
			return;
		}
		try {
			int pictureType = resolvePictureType(signatureUrl, imageBytes);
			XWPFRun run = paragraph.createRun();
			try (InputStream imageStream = new java.io.ByteArrayInputStream(imageBytes)) {
				run.addPicture(imageStream, pictureType, "buyer-signature.png",
						Units.toEMU(160), Units.toEMU(70));
			}
		} catch (Exception e) {
			log.warn("Cannot embed buyer signature image: {}", e.getMessage());
		}
	}

	private static void fillPrintedNameParagraph(XWPFParagraph paragraph, String displayName) {
		clearParagraphRuns(paragraph);
		ensureCenterAligned(paragraph);
		if (displayName.isBlank()) {
			return;
		}
		XWPFRun nameRun = paragraph.createRun();
		nameRun.setBold(true);
		nameRun.setText(displayName);
	}

	private static void ensureCenterAligned(XWPFParagraph paragraph) {
		paragraph.setAlignment(ParagraphAlignment.CENTER);
		paragraph.setIndentationLeft(0);
		paragraph.setIndentationRight(0);
	}

	private static void clearParagraphRuns(XWPFParagraph paragraph) {
		for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
			paragraph.removeRun(i);
		}
	}

	public static XWPFTableCell findBuyerSignatureCell(XWPFDocument document) {
		List<XWPFTable> tables = document.getTables();
		for (int t = tables.size() - 1; t >= 0; t--) {
			XWPFTable table = tables.get(t);
			for (XWPFTableRow row : table.getRows()) {
				if (row.getTableCells().size() < 2) {
					continue;
				}
				XWPFTableCell rightCell = row.getCell(1);
				if (cellContains(rightCell, "BÊN B") || cellContains(rightCell, PLACEHOLDER_SIGNATURE_IMAGE)) {
					return rightCell;
				}
			}
		}
		return null;
	}

	private static boolean cellContains(XWPFTableCell cell, String needle) {
		return cell.getParagraphs().stream()
				.map(XWPFParagraph::getText)
				.anyMatch(text -> text != null && text.contains(needle));
	}

	private static int resolvePictureType(String url, byte[] bytes) {
		String lower = url == null ? "" : url.toLowerCase();
		if (lower.contains(".png") || (bytes.length > 1 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50)) {
			return XWPFDocument.PICTURE_TYPE_PNG;
		}
		if (lower.contains(".gif")) {
			return XWPFDocument.PICTURE_TYPE_GIF;
		}
		return XWPFDocument.PICTURE_TYPE_JPEG;
	}

	private static byte[] loadImageBytes(String signatureUrl) {
		try {
			if (signatureUrl.startsWith("data:")) {
				int comma = signatureUrl.indexOf(',');
				if (comma < 0) {
					return null;
				}
				return Base64.getDecoder().decode(signatureUrl.substring(comma + 1));
			}
			try (InputStream is = URI.create(signatureUrl).toURL().openStream()) {
				return is.readAllBytes();
			}
		} catch (Exception e) {
			log.warn("Cannot load signature image: {}", e.getMessage());
			return null;
		}
	}
}
