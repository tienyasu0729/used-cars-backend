package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.StaffAssignment;

import java.util.Optional;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

	Optional<StaffAssignment> findFirstByUserIdAndActiveTrueOrderByIdDesc(Long userId);

}
