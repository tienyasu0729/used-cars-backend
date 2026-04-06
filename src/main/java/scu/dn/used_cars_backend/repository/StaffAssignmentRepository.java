package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.StaffAssignment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

	long countByBranchIdAndActiveTrue(int branchId);

	boolean existsByUserIdAndBranchIdAndActiveTrue(Long userId, Integer branchId);

	Optional<StaffAssignment> findFirstByUserIdAndActiveTrueOrderByIdDesc(Long userId);

	List<StaffAssignment> findByUserIdOrderByStartDateDesc(Long userId);

	boolean existsByUserIdAndBranchId(Long userId, Integer branchId);

	List<StaffAssignment> findByUserIdAndActiveTrue(Long userId);

	@Query("""
			select sa from StaffAssignment sa
			where sa.active = true and sa.userId in :userIds
			""")
	List<StaffAssignment> findActiveByUserIdIn(@Param("userIds") Collection<Long> userIds);

	/** Phân công còn hiệu lực tại chi nhánh (dùng khi Branches.manager_id chưa gán nhưng có StaffAssignment). */
	@Query("""
			select sa from StaffAssignment sa
			where sa.branchId = :branchId
				and sa.active = true
				and (sa.endDate is null or sa.endDate >= CURRENT_DATE)
			order by sa.id desc
			""")
	List<StaffAssignment> findCurrentlyActiveByBranchIdOrderByIdDesc(@Param("branchId") Integer branchId);

}
