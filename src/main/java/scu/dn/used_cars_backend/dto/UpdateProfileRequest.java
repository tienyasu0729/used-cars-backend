package scu.dn.used_cars_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Body cập nhật hồ sơ — map cột Users.
 * Record + compact constructor: strip, chuẩn hoá SĐT trước khi Bean Validation.
 */
public record UpdateProfileRequest(
		@NotBlank(message = "Họ tên không được để trống.")
		@Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự.")
		@Pattern(
				regexp = "(?U)^[\\p{L}\\p{M}0-9\\s.'’\\-]{2,100}$",
				message = "Họ tên chỉ gồm chữ cái (có dấu), số, khoảng trắng và các ký tự . ' - .")
		String name,
		@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự (trước khi chuẩn hoá).")
		@Pattern(
				regexp = "^$|^0[0-9]{9}$",
				message = "Số điện thoại phải đúng 10 chữ số và bắt đầu bằng 0 (hoặc để trống).")
		String phone,
		@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự.")
		@Pattern(
				regexp = "^[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]*$",
				message = "Địa chỉ không được chứa ký tự điều khiển.")
		String address,
		@PastOrPresent(message = "Ngày sinh không được ở tương lai.")
		LocalDate dateOfBirth,
		@Pattern(
				regexp = "^(|male|female|other)$",
				message = "Giới tính phải là male, female hoặc other (hoặc để trống).")
		String gender) {

	public UpdateProfileRequest {
		name = name == null ? "" : name.strip();
		if (phone != null && phone.isBlank()) {
			phone = null;
		}
		else if (phone != null) {
			phone = normalizeVnPhone(phone.strip());
		}
		if (address != null && address.isBlank()) {
			address = null;
		}
		else if (address != null) {
			address = address.strip();
		}
		if (gender != null && gender.isBlank()) {
			gender = null;
		}
		else if (gender != null) {
			gender = gender.strip().toLowerCase(Locale.ROOT);
		}
	}

	private static String normalizeVnPhone(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String t = raw.replaceAll("\\s+", "");
		if (t.startsWith("+84")) {
			t = "0" + t.substring(3).replaceAll("\\D", "");
		}
		else if (t.startsWith("84") && t.length() >= 2) {
			String digits = t.replaceAll("\\D", "");
			if (digits.startsWith("84") && digits.length() >= 11) {
				t = "0" + digits.substring(2, 11);
			}
			else if (digits.startsWith("84") && digits.length() == 10) {
				t = "0" + digits.substring(2);
			}
			else {
				t = digits;
			}
		}
		else {
			t = t.replaceAll("\\D", "");
		}
		return t.isEmpty() ? null : t;
	}
}
