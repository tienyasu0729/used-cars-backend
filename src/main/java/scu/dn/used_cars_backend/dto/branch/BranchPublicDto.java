package scu.dn.used_cars_backend.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Dữ liệu chi nhánh trả về client công khai (không lộ manager nội bộ). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchPublicDto {

	private Integer id;
	private String name;
	private String address;
	private String phone;
	private BigDecimal lat;
	private BigDecimal lng;

}
