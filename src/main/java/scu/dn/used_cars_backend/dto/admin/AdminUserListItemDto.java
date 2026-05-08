package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AdminUserListItemDto {
	Long id;
	String name;
	String email;
	String phone;
	String role;
	Integer branchId;
	String branchName;
	String status;
	/** URL avatar (Cloudinary hoặc null). */
	String avatarUrl;
	Instant createdAt;
}
