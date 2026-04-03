package scu.dn.used_cars_backend.service;

/**
 * Ngữ cảnh upload — server chọn folder (không tin client).
 */
public enum MediaUploadContext {

	/** Ảnh quản lý (xe, showroom, …) qua {@code /api/v1/manager/media/image}. */
	MANAGER_GENERAL("used-cars", false),

	/** Banner trang chủ — {@code /api/v1/admin/home-banners/upload-signature}. */
	HOME_BANNER("used-cars/home-banners", false),

	/** Avatar người dùng qua {@code POST /api/v1/users/me/avatar}; {@code scopeId} = userId. */
	AVATAR("used-cars/avatars", true);

	private final String cloudinaryFolder;
	private final boolean requiresScopeId;

	MediaUploadContext(String cloudinaryFolder, boolean requiresScopeId) {
		this.cloudinaryFolder = cloudinaryFolder;
		this.requiresScopeId = requiresScopeId;
	}

	public String cloudinaryFolder() {
		return cloudinaryFolder;
	}

	public boolean requiresScopeId() {
		return requiresScopeId;
	}
}
