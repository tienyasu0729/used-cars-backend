package scu.dn.used_cars_backend.booking.service;

import org.springframework.stereotype.Component;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.BookingContract;
import scu.dn.used_cars_backend.config.InstallmentContractProperties;
import scu.dn.used_cars_backend.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BookingContractFieldMapper {

	private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter VN_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	private final InstallmentContractProperties contractProperties;

	public BookingContractFieldMapper(InstallmentContractProperties contractProperties) {
		this.contractProperties = contractProperties;
	}

	public Map<String, String> buildFields(
			Booking booking,
			BookingContract contract,
			User customer,
			String termsBody) {
		Instant signedAt = contract.getSignedAt() != null ? contract.getSignedAt() : Instant.now();
		LocalDate contractDate = signedAt.atZone(VN_ZONE).toLocalDate();

		var branch = booking.getBranch();
		var seller = contractProperties.getSeller();

		String sellerAddress = seller.getAddress();
		if (sellerAddress.isBlank() && branch != null) {
			sellerAddress = safe(branch.getAddress());
		}
		String sellerRep = seller.getRepresentative();
		if (sellerRep.isBlank() && branch != null && branch.getManager() != null) {
			sellerRep = safe(branch.getManager().getName());
		}

		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("CONTRACT_NO", String.valueOf(booking.getId()));
		fields.put("TERMS_VERSION", safe(contract.getTermsVersion()));
		fields.put("DAY", String.valueOf(contractDate.getDayOfMonth()));
		fields.put("MONTH", String.valueOf(contractDate.getMonthValue()));
		fields.put("YEAR", String.valueOf(contractDate.getYear()));
		fields.put("BRANCH_NAME", branch != null ? safe(branch.getName()) : "—");
		fields.put("SELLER_COMPANY", seller.getCompanyName());
		fields.put("SELLER_TAX_CODE", seller.getTaxCode());
		fields.put("SELLER_ADDRESS", sellerAddress);
		fields.put("SELLER_REPRESENTATIVE", sellerRep);
		fields.put("CUSTOMER_NAME", customer != null ? safe(customer.getName()) : "—");
		fields.put("CUSTOMER_PHONE", customer != null ? safe(customer.getPhone()) : "—");
		fields.put("VEHICLE_TITLE", booking.getVehicle() != null ? safe(booking.getVehicle().getTitle()) : "—");
		fields.put("BOOKING_DATE", booking.getBookingDate() != null ? VN_DATE.format(booking.getBookingDate()) : "—");
		fields.put("TIME_SLOT", booking.getTimeSlot() != null ? booking.getTimeSlot().toString() : "—");
		fields.put("TERMS_BODY", termsBody != null ? termsBody : "");
		fields.put("SIGNED_AT", VN_DATETIME.format(signedAt.atZone(VN_ZONE)));
		return fields;
	}

	static String resolveCustomerSignatureName(BookingContract contract, User customer) {
		if ("type".equalsIgnoreCase(contract.getSignatureType())) {
			String typed = contract.getSignatureUrl();
			if (typed != null && !typed.isBlank()) {
				return typed.trim();
			}
		}
		if (customer != null && customer.getName() != null && !customer.getName().isBlank()) {
			return customer.getName().trim();
		}
		return "";
	}

	static String resolveCustomerSignatureImageUrl(BookingContract contract) {
		if ("draw".equalsIgnoreCase(contract.getSignatureType())) {
			return contract.getSignatureUrl();
		}
		return null;
	}

	private static String safe(Object value) {
		if (value == null) {
			return "—";
		}
		String s = value.toString().trim();
		return s.isEmpty() ? "—" : s;
	}
}
