package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.entity.BookingSlot;
import scu.dn.used_cars_backend.booking.repository.BookingSlotRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.manager.BookingSlotSettingDto;
import scu.dn.used_cars_backend.dto.manager.UpdateBookingSlotsRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.repository.BranchRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingSlotConfigService {

	private final BookingSlotRepository bookingSlotRepository;
	private final BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public List<BookingSlotSettingDto> listSlotsForBranch(int branchId, Boolean activeOnly) {
		List<BookingSlot> list = Boolean.TRUE.equals(activeOnly)
				? bookingSlotRepository.findByBranch_IdAndActiveTrueOrderBySlotTimeAsc(branchId)
				: bookingSlotRepository.findByBranch_IdOrderBySlotTimeAsc(branchId);
		return list.stream().map(BookingSlotConfigService::toDto).collect(Collectors.toList());
	}

	@Transactional
	public void updateSlotsForBranch(int branchId, UpdateBookingSlotsRequest request) {
		Branch branch = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		for (UpdateBookingSlotsRequest.SlotItem item : request.getSlots()) {
			BookingSlot slot = bookingSlotRepository.findByBranch_IdAndSlotTime(branchId, item.getSlotTime())
					.orElseGet(() -> {
						BookingSlot s = new BookingSlot();
						s.setBranch(branch);
						s.setSlotTime(item.getSlotTime());
						return s;
					});
			slot.setMaxBookings(item.getMaxBookings());
			slot.setActive(Boolean.TRUE.equals(item.getIsActive()));
			bookingSlotRepository.save(slot);
		}
	}

	private static BookingSlotSettingDto toDto(BookingSlot s) {
		return BookingSlotSettingDto.builder()
				.id(s.getId())
				.slotTime(s.getSlotTime())
				.maxBookings(s.getMaxBookings())
				.isActive(Boolean.TRUE.equals(s.getActive()))
				.build();
	}
}
