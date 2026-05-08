package scu.dn.used_cars_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scu.dn.used_cars_backend.entity.ConsultationRoutingState;

import java.util.Optional;

public interface ConsultationRoutingStateRepository extends JpaRepository<ConsultationRoutingState, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ConsultationRoutingState s where s.branchId = :branchId")
	Optional<ConsultationRoutingState> findForUpdate(@Param("branchId") int branchId);
}
