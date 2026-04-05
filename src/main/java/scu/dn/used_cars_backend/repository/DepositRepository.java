package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Deposit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

	Optional<Deposit> findByOrderId(long orderId);

	Optional<Deposit> findByGatewayTxnRef(String gatewayTxnRef);

	List<Deposit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	long countByVehicleIdAndStatusIn(long vehicleId, List<String> statuses);

	@Query("""
			select d.vehicleId, count(d) from Deposit d
			where d.vehicleId in :ids
			and d.status in ('Pending', 'Confirmed', 'AwaitingPayment')
			group by d.vehicleId
			""")
	List<Object[]> countActiveSalesHoldsGrouped(@Param("ids") List<Long> ids);

	List<Deposit> findByVehicleIdAndStatusIn(long vehicleId, Collection<String> statuses);

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

	@Query("""
			select d from Deposit d
			where d.customerId = :customerId
			and (:status is null or d.status = :status)
			and d.status not in ('AwaitingPayment')
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

	@Query(value = """
			select d from Deposit d
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			order by d.createdAt desc
			""",
			countQuery = """
			select count(d) from Deposit d
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			""")
	Page<Deposit> pageAllForTransactionHistory(
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway,
			Pageable pageable);

	@Query(value = """
			select d from Deposit d, Vehicle v
			where d.vehicleId = v.id and v.deleted = false and v.branch.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			order by d.createdAt desc
			""",
			countQuery = """
			select count(d) from Deposit d, Vehicle v
			where d.vehicleId = v.id and v.deleted = false and v.branch.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			""")
	Page<Deposit> pageForBranchForTransactionHistory(
			@Param("branchId") int branchId,
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway,
			Pageable pageable);

	@Query("""
			select count(d) from Deposit d
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			""")
	long countAllForTransactionHistory(
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway);

	@Query("""
			select count(d) from Deposit d, Vehicle v
			where d.vehicleId = v.id and v.deleted = false and v.branch.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and d.status in ('Confirmed','Pending'))
				or (:statusBucket = 'PENDING' and d.status = 'AwaitingPayment')
				or (:statusBucket = 'CANCELLED' and d.status = 'Cancelled'))
			and (:fromInclusive is null or d.createdAt >= :fromInclusive)
			and (:toExclusive is null or d.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(d.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'zalopay' or lower(trim(d.paymentMethod)) = 'zalopay'))
				or (lower(:gateway) = 'vnpay' and (lower(trim(coalesce(d.paymentGateway,''))) = 'vnpay' or lower(trim(d.paymentMethod)) = 'vnpay')))
			""")
	long countForBranchForTransactionHistory(
			@Param("branchId") int branchId,
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway);

	@Query("""
			select d from Deposit d
			where d.createdAt >= :fromInclusive and d.createdAt < :toExclusive
			order by d.createdAt desc
			""")
	List<Deposit> listForUnifiedByCreatedAtRange(
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive);

	@Query("""
			select d from Deposit d, Vehicle v
			where d.vehicleId = v.id and v.deleted = false
			and v.branch.id in :branchIds
			and d.createdAt >= :fromInclusive and d.createdAt < :toExclusive
			order by d.createdAt desc
			""")
	List<Deposit> listForUnifiedByCreatedAtRangeAndBranches(
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("branchIds") Collection<Integer> branchIds);
}
