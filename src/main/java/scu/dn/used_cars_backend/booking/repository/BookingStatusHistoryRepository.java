package scu.dn.used_cars_backend.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.booking.entity.BookingStatusHistory;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

	List<BookingStatusHistory> findByBooking_IdOrderByChangedAtAsc(long bookingId);
}
