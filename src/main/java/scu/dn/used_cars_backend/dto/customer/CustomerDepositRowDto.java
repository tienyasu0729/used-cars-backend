package scu.dn.used_cars_backend.dto.customer;

import scu.dn.used_cars_backend.entity.Deposit;

import java.time.format.DateTimeFormatter;

public record CustomerDepositRowDto(String id, String vehicleId, String customerId, long amount, String depositDate,
		String expiryDate, String status, String orderId) {

	private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	public static CustomerDepositRowDto from(Deposit d) {
		String st = d.getStatus() == null ? "Pending" : d.getStatus();
		if ("Converted".equals(st)) {
			st = "ConvertedToOrder";
		}
		String oid = d.getOrderId() == null ? null : String.valueOf(d.getOrderId());
		return new CustomerDepositRowDto(String.valueOf(d.getId()), String.valueOf(d.getVehicleId()),
				String.valueOf(d.getCustomerId()), d.getAmount().longValue(), d.getDepositDate().format(ISO_DATE),
				d.getExpiryDate().format(ISO_DATE), st, oid);
	}
}
