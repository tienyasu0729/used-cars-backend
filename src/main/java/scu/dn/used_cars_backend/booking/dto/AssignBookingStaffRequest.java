package scu.dn.used_cars_backend.booking.dto;

import lombok.Data;

/** Phân công / gỡ nhân viên phụ trách lịch hẹn (manager). {@code staffId == null} = chưa phân công. */
@Data
public class AssignBookingStaffRequest {

	private Long staffId;
}
