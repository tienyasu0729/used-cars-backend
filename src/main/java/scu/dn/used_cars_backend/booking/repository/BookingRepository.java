package scu.dn.used_cars_backend.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.booking.entity.Booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "vehicle", "vehicle.category", "vehicle.subcategory", "branch" })
	@Query("""
			select b from Booking b
			where b.customerId = :customerId
			and (:status is null or b.status = :status)
			""")
	Page<Booking> findByCustomerIdAndOptionalStatus(@Param("customerId") long customerId,
			@Param("status") String status, Pageable pageable);

	@EntityGraph(attributePaths = { "vehicle", "vehicle.category", "vehicle.subcategory", "branch" })
	@Query("""
			select b from Booking b
			where b.branch.id = :branchId
			and (:status is null or b.status = :status)
			""")
	Page<Booking> findByBranchIdAndOptionalStatus(@Param("branchId") int branchId, @Param("status") String status,
			Pageable pageable);

	@EntityGraph(attributePaths = { "vehicle", "vehicle.category", "vehicle.subcategory", "branch" })
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findWithDetailsById(@Param("id") long id);

	@Query("""
			select count(b) from Booking b
			where b.branch.id = :branchId
			and b.bookingDate = :date
			and b.timeSlot = :time
			and b.status in :statuses
			""")
	long countAtBranchSlot(@Param("branchId") int branchId, @Param("date") LocalDate date, @Param("time") LocalTime time,
			@Param("statuses") Collection<String> statuses);

	@Query("""
			select count(b) from Booking b
			where b.branch.id = :branchId
			and b.bookingDate = :date
			and b.timeSlot = :time
			and b.status in :statuses
			and b.id <> :excludeId
			""")
	long countAtBranchSlotExcluding(@Param("branchId") int branchId, @Param("date") LocalDate date,
			@Param("time") LocalTime time, @Param("statuses") Collection<String> statuses,
			@Param("excludeId") long excludeId);

	@EntityGraph(attributePaths = { "vehicle", "vehicle.category", "vehicle.subcategory", "branch" })
	@Query("""
			select b from Booking b
			where b.branch.id = :branchId
			and b.bookingDate = :date
			and b.status in ('Pending','Confirmed','Rescheduled')
			order by b.timeSlot asc, b.id asc
			""")
	java.util.List<Booking> findScheduleForBranchAndDate(@Param("branchId") int branchId, @Param("date") LocalDate date);

	@Query("""
			select count(b) from Booking b
			where b.customerId = :userId
			and b.status in ('Pending', 'Confirmed')
			""")
	long countUpcomingByCustomerId(@Param("userId") long userId);
}
