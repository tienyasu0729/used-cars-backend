package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.BranchWorkingHours;

import java.util.List;
import java.util.Optional;

public interface BranchWorkingHoursRepository extends JpaRepository<BranchWorkingHours, Integer> {

	List<BranchWorkingHours> findByBranch_IdOrderByDayOfWeekAsc(int branchId);

	Optional<BranchWorkingHours> findByBranch_IdAndDayOfWeek(int branchId, int dayOfWeek);
}
