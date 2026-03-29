package scu.dn.used_cars_backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileDto {

	private Long id;
	private String name;
	private String email;
	private String phone;
	private String address;
	private String role;
	private Integer branchId;
}
