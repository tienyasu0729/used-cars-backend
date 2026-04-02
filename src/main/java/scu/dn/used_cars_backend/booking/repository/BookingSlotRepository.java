package scu.dn.used_cars_backend.booking.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.booking.entity.BookingSlot;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, Integer> {

	List<BookingSlot> findByBranch_IdOrderBySlotTimeAsc(int branchId);

	List<BookingSlot> findByBranch_IdAndActiveTrueOrderBySlotTimeAsc(int branchId);

	Optional<BookingSlot> findByBranch_IdAndSlotTime(int branchId, LocalTime slotTime);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from BookingSlot s where s.branch.id = :branchId and s.slotTime = :slotTime and s.active = true")
	Optional<BookingSlot> findActiveForUpdate(@Param("branchId") int branchId, @Param("slotTime") LocalTime slotTime);
}
