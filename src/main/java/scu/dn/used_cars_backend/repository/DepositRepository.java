package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.Deposit;

import java.util.List;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

	List<Deposit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
