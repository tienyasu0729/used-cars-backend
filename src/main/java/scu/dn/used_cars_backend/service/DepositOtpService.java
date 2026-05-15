package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.dto.sales.DepositVerifyOtpRequest;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyResult;
import scu.dn.used_cars_backend.sms.service.OtpService;

@Service
@RequiredArgsConstructor
public class DepositOtpService {

	private static final String REFERENCE_TYPE = "deposit";

	private final OtpService otpService;
	private final DepositService depositService;
	private final DepositRepository depositRepository;

	public OtpResponse requestOtp(long userId, String phone) {
		return otpService.generateOtp(phone, REFERENCE_TYPE, userId);
	}

	@Transactional(rollbackFor = Exception.class)
	public CreateDepositResponse verifyOtpAndCreateDeposit(
			long actorUserId, String jwtRole,
			DepositVerifyOtpRequest req, String clientIp) {
		OtpVerifyResult otpResult = otpService.verifyOtp(
				req.getPhone(), req.getOtpCode(), REFERENCE_TYPE, actorUserId);

		CreateDepositResponse response = depositService.create(
				actorUserId, jwtRole, req.getDepositData(), clientIp);

		Deposit deposit = depositRepository.findById(response.getId()).orElse(null);
		if (deposit != null) {
			deposit.setOtpVerificationId(otpResult.getOtpId());
			depositRepository.save(deposit);
		}

		return response;
	}
}
