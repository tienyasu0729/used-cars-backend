package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Thông tin khách showroom (offline) — staff nhập tay khi khách chưa có tài khoản.
 * Dùng chung cho cả CreateDepositRequest và CreateOrderRequest.
 */
@Data
public class ShowroomCustomerInfo {

	@NotBlank
	@Size(max = 100)
	private String fullName;

	@NotBlank
	@Email
	@Size(max = 255)
	private String email;

	@NotBlank
	@Size(max = 20)
	private String phone;

	@NotBlank
	@Size(max = 500)
	private String address;
}
