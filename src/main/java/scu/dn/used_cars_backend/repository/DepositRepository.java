package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Deposit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

	Optional<Deposit> findByOrderId(long orderId);

	Optional<Deposit> findByGatewayTxnRef(String gatewayTxnRef);

	List<Deposit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	long countByVehicleIdAndStatusIn(long vehicleId, List<String> statuses);

	@Query("""
			select d from Deposit d
			where d.customerId = :customerId
			and (:status is null or d.status = :status)
			order by d.createdAt desc
			""")
	Page<Deposit> pageForCustomer(@Param("customerId") long customerId, @Param("status") String status, Pageable pageable);

	@Query("""
			select d from Deposit d, Vehicle v
			where d.vehicleId = v.id and v.branch.id = :branchId and v.deleted = false
			and (:status is null or d.status = :status)
			order by d.createdAt desc
			""")
	Page<Deposit> pageForBranch(@Param("branchId") int branchId, @Param("status") String status, Pageable pageable);

	@Query("""
			select d from Deposit d
			where (:status is null or d.status = :status)
			order by d.createdAt desc
			""")
	Page<Deposit> pageAll(@Param("status") String status, Pageable pageable);

	@Query("""
			select d from Deposit d
			where d.customerId = :customerId
			and (:status is null or d.status = :status)
			and not (d.status = 'Cancelled' and d.paymentGateway is not null
			         and d.notes like '%thanh toan khong thanh cong%')
			order by d.createdAt desc
			""")
	Page<Deposit> pageForCustomerClean(@Param("customerId") long customerId, @Param("status") String status,
			Pageable pageable);

	// Dùng cho ROLE_CUSTOMER: ẩn AwaitingPayment và Cancelled online
	@Query("""
			select d from Deposit d
			where d.customerId = :customerId
			and (:status is null or d.status = :status)
			and d.status not in ('AwaitingPayment')
			and not (d.status = 'Cancelled'
			         and d.paymentGateway is not null
			         and lower(d.paymentGateway) in ('vnpay', 'zalopay'))
			order by d.createdAt desc
			""")
	Page<Deposit> pageForCustomerVisible(@Param("customerId") long customerId, @Param("status") String status,
			Pageable pageable);

	long countByCustomerIdAndStatusIn(long customerId, List<String> statuses);

	// Tìm cả Pending và AwaitingPayment online deposits đã quá hạn
	@Query("""
			select d.id from Deposit d
			where d.status in ('Pending', 'AwaitingPayment')
			and lower(trim(coalesce(d.paymentGateway, ''))) in ('vnpay', 'zalopay')
			and d.createdAt < :cutoff
			""")
	List<Long> findPendingOnlineDepositIdsCreatedBefore(@Param("cutoff") Instant cutoff);
}
