package scu.dn.used_cars_backend.service.installment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.repository.InstallmentApplicationRepository;
import scu.dn.used_cars_backend.repository.LoanConfigRepository;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentContractDocxService {

	private static final String TEMPLATE_PATH = "templates/installment/mau-hd-tra-gop-mua-ban-hh.docx";

	private final InstallmentApplicationRepository applicationRepository;
	private final LoanConfigRepository loanConfigRepository;
	private final InstallmentContractFieldMapper fieldMapper;

	public byte[] generateContractDocx(Long applicationId) {
		InstallmentApplication app = applicationRepository.findByIdWithVehicleBranchAndDocuments(applicationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ho so."));

		var loanConfig = app.getLoanTermMonths() == null
				? java.util.Optional.<scu.dn.used_cars_backend.entity.LoanConfig>empty()
				: loanConfigRepository.findByTermMonths(app.getLoanTermMonths());

		Map<String, String> fields = fieldMapper.buildFields(app, loanConfig);

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

			String buyerName = InstallmentContractFieldMapper.resolveBuyerDisplayName(app);
			InstallmentSignatureImageSupport.embedBuyerSignatureBlock(document, app.getSignatureUrl(), buyerName);

			document.write(out);
			return out.toByteArray();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error generating contract DOCX for app #{}: {}", applicationId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi tao file Word hop dong.");
		}
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
		if (text.isEmpty()) {
			return;
		}
		String updated = applyPlaceholders(text, fields);
		if (updated.equals(text)) {
			return;
		}
		for (int i = runs.size() - 1; i > 0; i--) {
			paragraph.removeRun(i);
		}
		XWPFRun first = paragraph.getRuns().get(0);
		first.setText(updated, 0);
	}

	private static String applyPlaceholders(String text, Map<String, String> fields) {
		String updated = text;
		for (Map.Entry<String, String> entry : fields.entrySet()) {
			String token = "{{" + entry.getKey() + "}}";
			String value = entry.getValue() != null ? entry.getValue() : "";
			updated = updated.replace(token, value);
		}
		return updated;
	}
}
