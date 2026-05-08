package scu.dn.used_cars_backend.usedcarpurchase.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scu.dn.used_cars_backend.usedcarpurchase.entity.UsedCarPurchaseRequest;

public interface UsedCarPurchaseRequestRepository extends JpaRepository<UsedCarPurchaseRequest, Long> {

	Page<UsedCarPurchaseRequest> findByBranchId(Integer branchId, Pageable pageable);

	Page<UsedCarPurchaseRequest> findByBranchIdAndStatus(Integer branchId, String status, Pageable pageable);

	Page<UsedCarPurchaseRequest> findAll(Pageable pageable);

	Page<UsedCarPurchaseRequest> findAllByStatus(String status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from UsedCarPurchaseRequest r where r.id = :id")
	java.util.Optional<UsedCarPurchaseRequest> findByIdForUpdate(@Param("id") Long id);
}
