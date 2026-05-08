package scu.dn.used_cars_backend.dto.installment;

import lombok.Data;

@Data
public class BankWebhookRequest {
	private String loanId;
	private String status; // APPROVED | REJECTED
	private String rejectionReason;
	private String pdfUrl;
}
