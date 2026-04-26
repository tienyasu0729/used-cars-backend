package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId implements Serializable {

	@Column(name = "role_id")
	private Integer roleId;

	@Column(name = "permission_id")
	private Integer permissionId;
}
