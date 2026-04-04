package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.FinancialTransaction;

import java.time.Instant;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

	Optional<FinancialTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

	@Query("""
			select t from FinancialTransaction t
			where t.userId = :userId
			and (:type is null or t.type = :type)
			and (:fromInclusive is null or t.createdAt >= :fromInclusive)
			and (:toExclusive is null or t.createdAt < :toExclusive)
			order by t.createdAt desc
			""")
	Page<FinancialTransaction> pageForUser(@Param("userId") long userId, @Param("type") String type,
			@Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive, Pageable pageable);

	@Query("""
			select t from FinancialTransaction t
			where (:type is null or t.type = :type)
			and (:fromInclusive is null or t.createdAt >= :fromInclusive)
			and (:toExclusive is null or t.createdAt < :toExclusive)
			and (
			  (t.referenceType = 'Deposit' and exists (
			     select 1 from Deposit d, Vehicle v
			     where d.id = t.referenceId and d.vehicleId = v.id and v.branch.id = :branchId and v.deleted = false))
			  or (t.referenceType = 'Order' and exists (
			     select 1 from SalesOrder o where o.id = t.referenceId and o.branch.id = :branchId))
			)
			order by t.createdAt desc
			""")
	Page<FinancialTransaction> pageForBranch(@Param("branchId") int branchId, @Param("type") String type,
			@Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive, Pageable pageable);

	@Query("""
			select t from FinancialTransaction t
			where (:type is null or t.type = :type)
			and (:fromInclusive is null or t.createdAt >= :fromInclusive)
			and (:toExclusive is null or t.createdAt < :toExclusive)
			order by t.createdAt desc
			""")
	Page<FinancialTransaction> pageAll(@Param("type") String type, @Param("fromInclusive") Instant fromInclusive,
			@Param("toExclusive") Instant toExclusive, Pageable pageable);
}
