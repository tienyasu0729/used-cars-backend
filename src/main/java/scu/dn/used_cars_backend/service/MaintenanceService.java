package scu.dn.used_cars_backend.service;

// Service xử lý logic liên quan đến lịch sử bảo dưỡng xe.
// Bao gồm: lấy danh sách, tạo bản ghi mới, kiểm tra quyền chi nhánh.

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.CreateMaintenanceRequest;
import scu.dn.used_cars_backend.dto.vehicle.MaintenanceHistoryResponse;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleMaintenanceHistory;
import scu.dn.used_cars_backend.repository.VehicleMaintenanceHistoryRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

	private final VehicleMaintenanceHistoryRepository maintenanceRepo;
	private final VehicleRepository vehicleRepository;
	private final VehicleService vehicleService;

	/**
	 * Lấy danh sách bảo dưỡng của xe — phân trang, sắp xếp theo ngày giảm dần.
	 * Kiểm quyền: actor phải quản lý chi nhánh chứa xe (Admin: bỏ qua).
	 */
	@Transactional(readOnly = true)
	public Page<MaintenanceHistoryResponse> getMaintenanceHistory(long vehicleId, long actorUserId,
			boolean isAdmin, int page, int size) {
		// B1: kiểm tra xe tồn tại + quyền chi nhánh
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		vehicleService.assertCanManageBranchPublic(actorUserId, isAdmin, v.getBranch());

		// B2: truy vấn paginated
		Page<VehicleMaintenanceHistory> entities = maintenanceRepo
				.findByVehicle_IdOrderByMaintenanceDateDesc(vehicleId, PageRequest.of(page, size));

		// B3: map entity → DTO
		return entities.map(this::toResponse);
	}

	/**
	 * Tạo bản ghi bảo dưỡng mới cho xe.
	 * Kiểm quyền: actor phải quản lý chi nhánh chứa xe.
	 */
	@Transactional
	public MaintenanceHistoryResponse createMaintenanceRecord(long vehicleId, CreateMaintenanceRequest req,
			long actorUserId, boolean isAdmin) {
		// B1: kiểm tra xe tồn tại + quyền chi nhánh
		Vehicle v = vehicleRepository.findManagedDetailById(vehicleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		vehicleService.assertCanManageBranchPublic(actorUserId, isAdmin, v.getBranch());

		// B2: tạo entity từ DTO
		VehicleMaintenanceHistory record = new VehicleMaintenanceHistory();
		record.setVehicle(v);
		record.setMaintenanceDate(req.getMaintenanceDate());
		record.setDescription(req.getDescription());
		record.setCost(req.getCost() != null ? req.getCost() : BigDecimal.ZERO);
		record.setPerformedBy(req.getPerformedBy());

		// B3: lưu vào DB
		VehicleMaintenanceHistory saved = maintenanceRepo.save(record);
		return toResponse(saved);
	}

	/** Map entity → response DTO. */
	private MaintenanceHistoryResponse toResponse(VehicleMaintenanceHistory e) {
		return MaintenanceHistoryResponse.builder()
				.id(e.getId())
				.vehicleId(e.getVehicle().getId())
				.maintenanceDate(e.getMaintenanceDate())
				.description(e.getDescription())
				.cost(e.getCost())
				.performedBy(e.getPerformedBy())
				.createdAt(e.getCreatedAt())
				.build();
	}
}
