package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AdminRoleListItemDto {
	Integer id;
	String name;
	long userCount;
	List<Integer> permissionIds;
	List<String> permissions;
	/** true nếu là role seed hệ thống — không cho sửa/xóa qua API custom role. */
	boolean systemRole;
}
