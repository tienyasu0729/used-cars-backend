package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.SalesOrder;

import java.time.LocalDate;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

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
