package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import scu.dn.used_cars_backend.entity.InstallmentApplication;

import java.util.List;

@Repository
public interface InstallmentApplicationRepository extends JpaRepository<InstallmentApplication, Long>, JpaSpecificationExecutor<InstallmentApplication> {
	List<InstallmentApplication> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
	
	java.util.Optional<InstallmentApplication> findByBankLoanId(String bankLoanId);
}
