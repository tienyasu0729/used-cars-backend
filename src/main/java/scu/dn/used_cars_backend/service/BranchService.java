package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.branch.BranchPublicDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public List<BranchPublicDto> listPublic() {
		return branchRepository.findAllByDeletedFalseOrderByIdAsc().stream()
				.map(BranchService::toPublicDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public BranchPublicDto getPublicById(int id) {
		Branch b = branchRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		return toPublicDto(b);
	}

	private static BranchPublicDto toPublicDto(Branch b) {
		return BranchPublicDto.builder()
				.id(b.getId())
				.name(b.getName())
				.address(b.getAddress())
				.phone(b.getPhone())
				.lat(b.getLat())
				.lng(b.getLng())
				.build();
	}

}
