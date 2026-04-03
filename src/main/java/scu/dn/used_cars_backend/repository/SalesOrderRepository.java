package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.SalesOrder;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

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
}
