package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import scu.dn.used_cars_backend.entity.InstallmentStatusHistory;

import java.util.List;

@Repository
public interface InstallmentStatusHistoryRepository extends JpaRepository<InstallmentStatusHistory, Long> {
	List<InstallmentStatusHistory> findByApplicationIdOrderByChangedAtDesc(Long applicationId);
}
