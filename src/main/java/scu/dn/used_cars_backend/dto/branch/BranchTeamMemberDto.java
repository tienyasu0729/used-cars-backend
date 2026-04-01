package scu.dn.used_cars_backend.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thông tin hiển thị công khai trên trang chi nhánh (không email). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchTeamMemberDto {

	private String name;
	private String role;
	/** URL tương đối (vd. /uploads/avatars/1.jpg) hoặc null. */
	private String avatarUrl;
}
