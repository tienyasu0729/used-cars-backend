package scu.dn.used_cars_backend.booking;

import java.util.List;

public final class BookingSlotCounting {

	public static final List<String> BRANCH_OCCUPIED_STATUSES = List.of(
			"AwaitingContract", "Pending", "Confirmed", "Rescheduled");

	public static final List<String> VEHICLE_TRIPLE_OCCUPIED_STATUSES = List.of(
			"AwaitingContract", "Pending", "Confirmed", "Rescheduled", "Completed");

	private BookingSlotCounting() {
	}
}
