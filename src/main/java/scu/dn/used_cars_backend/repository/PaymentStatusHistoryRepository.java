package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.PaymentStatusHistory;

import java.util.List;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {

	List<PaymentStatusHistory> findByTargetKindAndTargetIdOrderByCreatedAtDesc(String targetKind, long targetId);
}
