package scu.dn.used_cars_backend.service;

// Service này load danh sách permission của từng role từ DB và cache lại.
// Mỗi request có JWT, filter gọi service này để lấy permission authorities.
// Cache tự hết hạn sau 5 phút hoặc bị xóa khi admin cập nhật quyền role.

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.entity.RolePermission;
import scu.dn.used_cars_backend.repository.RolePermissionRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RolePermissionCacheService {

	private static final String CACHE_NAME = "rolePermissions";

	private final RolePermissionRepository rolePermissionRepository;
	private final CacheManager cacheManager;

	/**
	 * Lấy danh sách permission authorities cho role name (có cache).
	 * Format: PERMISSION_MODULE_ACTION (VD: PERMISSION_VEHICLES_CREATE)
	 */
	@SuppressWarnings("unchecked")
	public List<SimpleGrantedAuthority> getPermissionAuthorities(String roleName) {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			Cache.ValueWrapper cached = cache.get(roleName);
			if (cached != null) {
				return (List<SimpleGrantedAuthority>) cached.get();
			}
		}

		// Cache miss — query DB
		List<RolePermission> rolePerms = rolePermissionRepository
				.findAllByRole_NameIgnoreCaseWithPermission(roleName);

		List<SimpleGrantedAuthority> authorities = new ArrayList<>();
		for (RolePermission rp : rolePerms) {
			String module = rp.getPermission().getModule().toUpperCase();
			String action = rp.getPermission().getAction().toUpperCase();
			authorities.add(new SimpleGrantedAuthority("PERMISSION_" + module + "_" + action));
		}

		// Lưu vào cache
		if (cache != null) {
			cache.put(roleName, authorities);
		}

		return authorities;
	}

	/**
	 * Xóa cache permission của role khi admin cập nhật quyền.
	 */
	public void evictCache(String roleName) {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			cache.evict(roleName);
		}
	}
}
