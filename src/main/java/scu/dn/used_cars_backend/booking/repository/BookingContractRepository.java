package scu.dn.used_cars_backend.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.booking.entity.BookingContract;

import java.util.Optional;

public interface BookingContractRepository extends JpaRepository<BookingContract, Long> {

	Optional<BookingContract> findByBooking_Id(long bookingId);

	boolean existsByBooking_Id(long bookingId);
}
