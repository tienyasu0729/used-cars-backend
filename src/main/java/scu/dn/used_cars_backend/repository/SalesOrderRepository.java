package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.SalesOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

	@Query("SELECT o FROM SalesOrder o JOIN FETCH o.branch WHERE o.id = :id")
	Optional<SalesOrder> findByIdWithBranch(@Param("id") long id);

	@Query("SELECT COUNT(o) FROM SalesOrder o WHERE o.branch.id = :branchId AND o.status <> 'Cancelled'")
	long countOrdersExcludingCancelled(@Param("branchId") Integer branchId);

	/** Đơn coi là đã bán / ghi nhận doanh thu: không Pending, không Cancelled. */
	@Query("SELECT COUNT(o) FROM SalesOrder o WHERE o.branch.id = :branchId AND o.status NOT IN ('Cancelled', 'Pending')")
	long countSoldExcludingPendingAndCancelled(@Param("branchId") Integer branchId);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM SalesOrder o WHERE o.branch.id = :branchId AND o.status NOT IN ('Cancelled', 'Pending')")
	BigDecimal sumRevenueExcludingPendingAndCancelled(@Param("branchId") Integer branchId);

	/** Đếm đơn tạo trong khoảng ngày [start, end] tại chi nhánh, không tính đã hủy (theo ngày lịch trên created_at). */
	@Query(value = """
			SELECT COUNT(*)
			FROM Orders o
			WHERE o.branch_id = :branchId
			AND CAST(o.created_at AS DATE) BETWEEN :start AND :end
			AND o.status <> 'Cancelled'
			""", nativeQuery = true)
	long countCreatedDateBetweenAtBranchExcludingCancelled(@Param("branchId") int branchId,
			@Param("start") LocalDate start, @Param("end") LocalDate end);

	@Query("select count(o) from SalesOrder o where o.branch.id = :branchId and o.status <> 'Cancelled' and o.createdAt >= :fromInclusive")
	long countSinceAtBranch(@Param("branchId") int branchId, @Param("fromInclusive") Instant fromInclusive);

	long countByCustomerId(long customerId);

	@Query(value = "SELECT MAX(o.order_number) FROM Orders o WHERE o.order_number LIKE CONCAT(CONCAT('ORD-', :ymd), '-%')", nativeQuery = true)
	String findMaxOrderNumberForYmd(@Param("ymd") String ymd);

	@EntityGraph(attributePaths = { "branch", "vehicle", "vehicle.category", "vehicle.subcategory" })
	@Query("select o from SalesOrder o where o.id = :id")
	Optional<SalesOrder> findByIdWithGraph(@Param("id") long id);

	@Query("""
			select o from SalesOrder o join o.vehicle v
			where o.customerId = :customerId
			and (:status is null or o.status = :status)
			and (:search is null or :search = '' or lower(o.orderNumber) like lower(concat('%', :search, '%'))
				or lower(v.title) like lower(concat('%', :search, '%')))
			order by o.createdAt desc
			""")
	Page<SalesOrder> pageForCustomer(@Param("customerId") long customerId, @Param("status") String status,
			@Param("search") String search, Pageable pageable);

	@Query("""
			select o from SalesOrder o join o.vehicle v
			where o.branch.id = :branchId
			and (:status is null or o.status = :status)
			and (:search is null or :search = '' or lower(o.orderNumber) like lower(concat('%', :search, '%'))
				or lower(v.title) like lower(concat('%', :search, '%')))
			order by o.createdAt desc
			""")
	Page<SalesOrder> pageForBranch(@Param("branchId") int branchId, @Param("status") String status,
			@Param("search") String search, Pageable pageable);

	@Query("""
			select o from SalesOrder o join o.vehicle v
			where (:status is null or o.status = :status)
			and (:search is null or :search = '' or lower(o.orderNumber) like lower(concat('%', :search, '%'))
				or lower(v.title) like lower(concat('%', :search, '%')))
			order by o.createdAt desc
			""")
	Page<SalesOrder> pageAll(@Param("status") String status, @Param("search") String search, Pageable pageable);

	@Query("select coalesce(sum(o.totalPrice), 0) from SalesOrder o where o.status = 'Completed'")
	BigDecimal sumTotalPriceCompletedAll();

	@Query("select coalesce(sum(o.totalPrice), 0) from SalesOrder o where o.status = 'Completed' and o.branch.id = :branchId and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive")
	BigDecimal sumCompletedInBranchBetween(@Param("branchId") int branchId, @Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive);

	@Query("select count(o) from SalesOrder o where o.status = 'Completed' and o.branch.id = :branchId and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive")
	long countCompletedInBranchBetween(@Param("branchId") int branchId, @Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive);

	@Query("select coalesce(sum(o.totalPrice), 0) from SalesOrder o where o.status = 'Completed' and o.createdAt >= :fromInclusive and o.createdAt < :toExclusive")
	BigDecimal sumCompletedAllBetween(@Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive);
}
