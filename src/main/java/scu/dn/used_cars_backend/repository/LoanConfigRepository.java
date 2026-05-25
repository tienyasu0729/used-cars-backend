package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scu.dn.used_cars_backend.entity.LoanConfig;

import java.util.List;
import java.util.Optional;

public interface LoanConfigRepository extends JpaRepository<LoanConfig, Long> {

	List<LoanConfig> findByActiveTrueOrderByTermMonthsAsc();

	Optional<LoanConfig> findByTermMonths(Integer termMonths);
}
