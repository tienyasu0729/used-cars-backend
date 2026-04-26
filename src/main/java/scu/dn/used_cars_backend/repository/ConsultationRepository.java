package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Consultation;

import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long>, JpaSpecificationExecutor<Consultation> {

	/** Phiếu chờ mới nhất của khách (gắn chat — tiếp nhận từ StaffChat). */
	@EntityGraph(attributePaths = { "vehicle", "vehicle.branch" })
	Optional<Consultation> findTopByCustomer_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(Long customerId, String status);

	// KPI staff: chỉ pending có xe thuộc chi nhánh — phiếu không xe không tính.
	@Query("""
			select count(c) from Consultation c
			join c.vehicle v
			where lower(c.status) = 'pending' and v.branch.id = :branchId
			""")
	long countPendingByVehicleBranchId(@Param("branchId") int branchId);

	@EntityGraph(attributePaths = { "customer", "vehicle", "vehicle.branch", "assignedStaff" })
	@Query("select c from Consultation c where c.id = :id")
	Optional<Consultation> findWithDetailsById(@Param("id") Long id);

}
