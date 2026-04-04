package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.OrderPayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

	Optional<OrderPayment> findByTransactionRef(String transactionRef);

	@Query("SELECT p FROM OrderPayment p JOIN FETCH p.order o JOIN FETCH o.branch WHERE p.id = :id")
	Optional<OrderPayment> findByIdWithOrderAndBranch(@Param("id") long id);

	@Query("SELECT p FROM OrderPayment p JOIN FETCH p.order o JOIN FETCH o.branch WHERE o.id = :orderId ORDER BY p.id ASC")
	List<OrderPayment> findByOrderIdWithOrderAndBranch(@Param("orderId") long orderId);

	@Query("select coalesce(sum(p.amount), 0) from OrderPayment p where p.order.id = :orderId and p.status = 'Completed'")
	BigDecimal sumCompletedAmountByOrderId(@Param("orderId") long orderId);
}
