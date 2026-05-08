package scu.dn.used_cars_backend.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "RolePermissions")
public class RolePermission {

	@EmbeddedId
	private RolePermissionId id = new RolePermissionId();

	@MapsId("roleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id")
	private Role role;

	@MapsId("permissionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "permission_id")
	private Permission permission;
}
