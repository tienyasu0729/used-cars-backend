package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.OrderPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

	Optional<OrderPayment> findByTransactionRef(String transactionRef);

	@Query("SELECT p FROM OrderPayment p JOIN FETCH p.order o JOIN FETCH o.branch JOIN FETCH o.vehicle WHERE p.id = :id")
	Optional<OrderPayment> findByIdWithOrderAndBranch(@Param("id") long id);

	@Query("SELECT p FROM OrderPayment p JOIN FETCH p.order o JOIN FETCH o.branch WHERE o.id = :orderId ORDER BY p.id ASC")
	List<OrderPayment> findByOrderIdWithOrderAndBranch(@Param("orderId") long orderId);

	@Query("select coalesce(sum(p.amount), 0) from OrderPayment p where p.order.id = :orderId and p.status = 'Completed'")
	BigDecimal sumCompletedAmountByOrderId(@Param("orderId") long orderId);

	@Query("""
			select p.id from OrderPayment p
			where p.status = 'Pending'
			and lower(trim(p.paymentMethod)) in ('vnpay', 'zalopay')
			and p.createdAt < :cutoff
			""")
	List<Long> findPendingOnlineOrderPaymentIdsCreatedBefore(@Param("cutoff") Instant cutoff);

	@Query(value = """
			select p from OrderPayment p
			join fetch p.order o
			join fetch o.branch
			join fetch o.vehicle
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			order by p.createdAt desc
			""",
			countQuery = """
			select count(p) from OrderPayment p
			join p.order o
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			""")
	Page<OrderPayment> pageAllWithOrder(
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway,
			Pageable pageable);

	@Query(value = """
			select p from OrderPayment p
			join fetch p.order o
			join fetch o.branch b
			join fetch o.vehicle
			where b.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			order by p.createdAt desc
			""",
			countQuery = """
			select count(p) from OrderPayment p
			join p.order o
			join o.branch b
			where b.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			""")
	Page<OrderPayment> pageByBranchWithOrder(
			@Param("branchId") int branchId,
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway,
			Pageable pageable);

	@Query("""
			select count(p) from OrderPayment p
			join p.order o
			where (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			""")
	long countAllWithFilters(
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway);

	@Query("""
			select count(p) from OrderPayment p
			join p.order o
			join o.branch b
			where b.id = :branchId
			and (:statusBucket is null
				or (:statusBucket = 'COMPLETED' and p.status = 'Completed')
				or (:statusBucket = 'PENDING' and p.status = 'Pending')
				or (:statusBucket = 'CANCELLED' and p.status in ('Cancelled','Refunded')))
			and (:fromInclusive is null or p.createdAt >= :fromInclusive)
			and (:toExclusive is null or p.createdAt < :toExclusive)
			and (:gateway is null
				or (lower(:gateway) = 'cash' and lower(trim(p.paymentMethod)) = 'cash')
				or (lower(:gateway) = 'zalopay' and lower(trim(p.paymentMethod)) = 'zalopay')
				or (lower(:gateway) = 'vnpay' and lower(trim(p.paymentMethod)) = 'vnpay'))
			""")
	long countByBranchWithFilters(
			@Param("branchId") int branchId,
			@Param("statusBucket") String statusBucket,
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("gateway") String gateway);

	@Query("""
			select p from OrderPayment p
			join fetch p.order o
			join fetch o.branch
			join fetch o.vehicle
			where p.createdAt >= :fromInclusive and p.createdAt < :toExclusive
			order by p.createdAt desc
			""")
	List<OrderPayment> listForUnifiedByCreatedAtRange(
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive);

	@Query("""
			select p from OrderPayment p
			join fetch p.order o
			join fetch o.branch b
			join fetch o.vehicle
			where p.createdAt >= :fromInclusive and p.createdAt < :toExclusive
			and b.id in :branchIds
			order by p.createdAt desc
			""")
	List<OrderPayment> listForUnifiedByCreatedAtRangeAndBranches(
			@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive,
			@Param("branchIds") Collection<Integer> branchIds);
}
