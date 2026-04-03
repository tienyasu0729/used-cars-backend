package scu.dn.used_cars_backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
	/** URL ảnh đại diện (Cloudinary secure_url hoặc URL tuyệt đối khác) hoặc null. */
	private String avatarUrl;
	private LocalDate dateOfBirth;
	/** male | female | other */
	private String gender;
	/** true → client phải chuyển sang màn đặt mật khẩu mới trước khi dùng app. */
	private Boolean passwordChangeRequired;
}
