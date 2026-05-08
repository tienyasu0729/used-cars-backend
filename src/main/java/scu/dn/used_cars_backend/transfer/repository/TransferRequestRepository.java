package scu.dn.used_cars_backend.transfer.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.transfer.entity.TransferRequest;

import java.util.Collection;
import java.util.Optional;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

	boolean existsByVehicle_IdAndStatusIn(Long vehicleId, Collection<String> statuses);

	@EntityGraph(attributePaths = { "vehicle" })
	@Query("""
			select t from TransferRequest t
			where (:branchId is null or t.fromBranchId = :branchId or t.toBranchId = :branchId)
			and (:status is null or :status = '' or t.status = :status)
			order by t.createdAt desc
			""")
	Page<TransferRequest> pageByScope(@Param("branchId") Integer branchId, @Param("status") String status,
			Pageable pageable);

	@EntityGraph(attributePaths = { "vehicle" })
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TransferRequest t where t.id = :id")
	Optional<TransferRequest> findByIdForUpdate(@Param("id") Long id);

}
