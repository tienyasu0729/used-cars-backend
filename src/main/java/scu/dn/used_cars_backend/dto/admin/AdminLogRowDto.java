package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminLogRowDto {
	String id;
	String user;
	String action;
	String module;
	String timestamp;
}
