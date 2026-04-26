package scu.dn.used_cars_backend.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.transfer.entity.TransferApprovalHistory;

import java.util.List;

public interface TransferApprovalHistoryRepository extends JpaRepository<TransferApprovalHistory, Long> {

	List<TransferApprovalHistory> findByTransfer_IdOrderByActedAtAsc(Long transferId);

}
