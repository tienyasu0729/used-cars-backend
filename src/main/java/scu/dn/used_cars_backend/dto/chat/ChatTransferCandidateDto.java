package scu.dn.used_cars_backend.dto.chat;

/** Ứng viên nhận chuyển giao hội thoại chat (NV/QL cùng chi nhánh hoặc QL chi nhánh khác). */
public record ChatTransferCandidateDto(long userId, String name, String roleLabel, String transferGroup) {

	/** Tư vấn viên cùng chi nhánh với quản lý đang thao tác. */
	public static final String GROUP_SAME_BRANCH_SALES = "SAME_BRANCH_SALES";
	/** Quản lý chi nhánh tại cửa hàng khác. */
	public static final String GROUP_OTHER_BRANCH_MANAGER = "OTHER_BRANCH_MANAGER";
}
