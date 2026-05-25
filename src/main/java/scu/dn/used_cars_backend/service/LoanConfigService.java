package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.dto.installment.LoanConfigDTO;
import scu.dn.used_cars_backend.dto.installment.SaveLoanConfigRequest;
import scu.dn.used_cars_backend.entity.LoanConfig;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.repository.LoanConfigRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanConfigService {

	private final LoanConfigRepository loanConfigRepository;

	public List<LoanConfigDTO> getAllActiveConfigs() {
		return loanConfigRepository.findByActiveTrueOrderByTermMonthsAsc()
				.stream()
				.map(this::toDTO)
				.collect(Collectors.toList());
	}

	public List<LoanConfigDTO> getAllConfigs() {
		return loanConfigRepository.findAll()
				.stream()
				.map(this::toDTO)
				.collect(Collectors.toList());
	}

	public Optional<LoanConfigDTO> getByTermMonths(Integer termMonths) {
		return loanConfigRepository.findByTermMonths(termMonths).map(this::toDTO);
	}

	@Transactional
	public LoanConfigDTO create(SaveLoanConfigRequest request) {
		if (loanConfigRepository.findByTermMonths(request.getTermMonths()).isPresent()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Loan config for termMonths=" + request.getTermMonths() + " already exists.");
		}
		LoanConfig entity = new LoanConfig();
		entity.setTermMonths(request.getTermMonths());
		entity.setInterestRatePercent(request.getInterestRatePercent());
		entity.setMinDownPaymentPercent(request.getMinDownPaymentPercent());
		entity.setActive(request.getActive() != null ? request.getActive() : true);
		entity.setDescription(request.getDescription());
		return toDTO(loanConfigRepository.save(entity));
	}

	@Transactional
	public LoanConfigDTO update(Long id, SaveLoanConfigRequest request) {
		LoanConfig entity = loanConfigRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "LoanConfig not found."));
		Optional<LoanConfig> existing = loanConfigRepository.findByTermMonths(request.getTermMonths());
		if (existing.isPresent() && !existing.get().getId().equals(id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Another loan config for termMonths=" + request.getTermMonths() + " already exists.");
		}
		entity.setTermMonths(request.getTermMonths());
		entity.setInterestRatePercent(request.getInterestRatePercent());
		entity.setMinDownPaymentPercent(request.getMinDownPaymentPercent());
		if (request.getActive() != null) entity.setActive(request.getActive());
		entity.setDescription(request.getDescription());
		return toDTO(loanConfigRepository.save(entity));
	}

	@Transactional
	public void delete(Long id) {
		if (!loanConfigRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "LoanConfig not found.");
		}
		loanConfigRepository.deleteById(id);
	}

	private LoanConfigDTO toDTO(LoanConfig entity) {
		return LoanConfigDTO.builder()
				.id(entity.getId())
				.termMonths(entity.getTermMonths())
				.interestRatePercent(entity.getInterestRatePercent())
				.minDownPaymentPercent(entity.getMinDownPaymentPercent())
				.active(entity.getActive())
				.description(entity.getDescription())
				.build();
	}
}
