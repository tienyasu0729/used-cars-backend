package scu.dn.used_cars_backend.service.installment;

import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.fonts.BestMatchingMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public final class InstallmentContractPdfConverter {

	private InstallmentContractPdfConverter() {
	}

	public static byte[] convertDocxToPdf(byte[] docxBytes) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(docxBytes);
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			WordprocessingMLPackage pkg = WordprocessingMLPackage.load(in);
			Mapper fontMapper = new BestMatchingMapper();
			pkg.setFontMapper(fontMapper);
			Docx4J.toPDF(pkg, out);
			byte[] pdf = out.toByteArray();
			if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
				throw new IllegalStateException("DOCX to PDF conversion did not produce a valid PDF stream");
			}
			return pdf;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("DOCX to PDF conversion failed: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Loi khi chuyen hop dong Word sang PDF.");
		}
	}
}
