package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.installment.CreateInstallmentPreDepositRequest;
import scu.dn.used_cars_backend.dto.installment.InstallmentApplicationResponse;
import scu.dn.used_cars_backend.dto.installment.InstallmentDocumentResponse;
import scu.dn.used_cars_backend.dto.installment.InstallmentSubmitEligibilityResponse;
import scu.dn.used_cars_backend.dto.installment.RejectInstallmentApplicationRequest;
import scu.dn.used_cars_backend.dto.installment.SaveInstallmentApplicationRequest;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.InstallmentService;
import scu.dn.used_cars_backend.service.LoanContractService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/installments/applications")
@RequiredArgsConstructor
public class InstallmentController {

	private final InstallmentService installmentService;
	private final LoanContractService loanContractService;

	@PostMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> createApplication(
			@Valid @RequestBody SaveInstallmentApplicationRequest request, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		InstallmentApplicationResponse res = installmentService.saveApplication(uid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res));
	}

	@PutMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> updateApplication(
			@PathVariable Long id,
			@Valid @RequestBody SaveInstallmentApplicationRequest request, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		InstallmentApplicationResponse res = installmentService.updateApplication(uid, id, request);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> getApplication(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		InstallmentApplicationResponse res = installmentService.getApplication(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/{id}/restore")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> restoreApplicationFromPaymentCache(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		InstallmentApplicationResponse res = installmentService.restoreApplicationFromPaymentCache(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/restore-by-deposit/{depositId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> restoreApplicationByDeposit(
			@PathVariable Long depositId, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		InstallmentApplicationResponse res = installmentService.restoreApplicationFromDepositCache(uid, role, depositId);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/{id}/submit-eligibility")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentSubmitEligibilityResponse>> getSubmitEligibility(
			@PathVariable Long id,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		InstallmentSubmitEligibilityResponse res = installmentService.getSubmitEligibility(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<List<InstallmentApplicationResponse>>> getMyApplications(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String q,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		var resultPage = installmentService.searchMyApplications(uid, page, size, status, q);
		PageMetaDto meta = PageMetaDto.builder()
				.page(resultPage.getNumber())
				.size(resultPage.getSize())
				.totalElements(resultPage.getTotalElements())
				.totalPages(resultPage.getTotalPages())
				.build();
		return ResponseEntity.ok(ApiResponse.success(resultPage.getContent(), meta));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<List<InstallmentApplicationResponse>>> getAllApplications(
			@RequestParam(required = false) String status, Authentication auth) {
		List<InstallmentApplicationResponse> list = installmentService.getAllApplications(status);
		return ResponseEntity.ok(ApiResponse.success(list));
	}

	@PostMapping("/on-behalf")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<InstallmentApplicationResponse>> createOnBehalf(
			@Valid @RequestBody SaveInstallmentApplicationRequest request,
			@RequestParam("customerId") Long customerId,
			Authentication auth) {
		long staffId = AuthenticationDetailsUtils.requireUserId(auth);
		InstallmentApplicationResponse res = installmentService.saveApplicationOnBehalf(staffId, customerId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res));
	}

	@PostMapping("/{id}/complete")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<Void>> completeApplication(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		installmentService.completeApplication(uid, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/documents")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<InstallmentDocumentResponse>> uploadDocument(
			@PathVariable Long id,
			@RequestParam("documentType") String documentType,
			@RequestParam("file") MultipartFile file,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		InstallmentDocumentResponse res = installmentService.uploadDocument(uid, id, documentType, file);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res));
	}

	@GetMapping("/{id}/documents")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<List<InstallmentDocumentResponse>>> getDocuments(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		InstallmentApplicationResponse res = installmentService.getApplication(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(res.getDocuments()));
	}

	@DeleteMapping("/{id}/documents/{documentId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<Void>> deleteDocument(
			@PathVariable Long id,
			@PathVariable Long documentId,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		installmentService.deleteDocument(uid, id, documentId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/mark-bank-processing")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<Void>> markBankProcessing(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String userName = auth.getName();
		installmentService.markBankProcessing(uid, userName, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<Void>> approveApplication(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String userName = auth.getName();
		installmentService.approveApplication(uid, userName, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<Void>> rejectApplication(
			@PathVariable Long id,
			@Valid @RequestBody RejectInstallmentApplicationRequest request,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String userName = auth.getName();
		installmentService.rejectApplication(uid, userName, id, request.getRejectionReason());
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/pre-deposit")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<CreateDepositResponse>> createPreDeposit(
			@PathVariable Long id,
			@Valid @RequestBody CreateInstallmentPreDepositRequest request,
			Authentication auth,
			jakarta.servlet.http.HttpServletRequest httpRequest) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		CreateDepositResponse res = installmentService.createPreDeposit(uid, role, id, request, httpRequest);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res));
	}

	// Phase 5: Liên kết deposit (cọc thiện chí) với hồ sơ trả góp
	@PostMapping("/{id}/link-deposit")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> linkDeposit(
			@PathVariable Long id,
			@RequestParam Long depositId,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		installmentService.linkDeposit(uid, id, depositId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	// Phase 5: Xác nhận cọc thiện chí đã thanh toán
	@PostMapping("/{id}/deposit-paid")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> handleDepositPaid(
			@PathVariable Long id, Authentication auth) {
		installmentService.handleDepositPaid(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	// Phase 5: Hủy hồ sơ trả góp
	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER', 'CUSTOMER')")
	public ResponseEntity<ApiResponse<Void>> cancelApplication(
			@PathVariable Long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		installmentService.cancelApplication(uid, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	// === Contract Export Endpoints ===

	@GetMapping("/{id}/export/contract-pdf")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<byte[]> exportContractPdf(@PathVariable Long id) {
		byte[] pdf = loanContractService.generateContractPdf(id);
		return ResponseEntity.ok()
				.header("Content-Type", "application/pdf")
				.header("Content-Disposition", "attachment; filename=loan_contract_" + id + ".pdf")
				.body(pdf);
	}

	@GetMapping("/{id}/export/identity-docs")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<byte[]> exportIdentityDocs(@PathVariable Long id) {
		byte[] zip = loanContractService.generateIdentityDocsZip(id);
		return ResponseEntity.ok()
				.header("Content-Type", "application/zip")
				.header("Content-Disposition", "attachment; filename=identity_docs_" + id + ".zip")
				.body(zip);
	}

	@GetMapping("/{id}/export/full-package")
	@PreAuthorize("hasAnyRole('ADMIN', 'SALESSTAFF', 'BRANCHMANAGER')")
	public ResponseEntity<byte[]> exportFullPackage(@PathVariable Long id) {
		byte[] zip = loanContractService.generateFullPackageZip(id);
		return ResponseEntity.ok()
				.header("Content-Type", "application/zip")
				.header("Content-Disposition", "attachment; filename=loan_package_" + id + ".zip")
				.body(zip);
	}
}
