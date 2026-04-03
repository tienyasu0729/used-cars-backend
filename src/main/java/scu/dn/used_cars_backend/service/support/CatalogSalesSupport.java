package scu.dn.used_cars_backend.service.support;

import scu.dn.used_cars_backend.dto.admin.CatalogSalesBrandRowDto;
import scu.dn.used_cars_backend.dto.admin.CatalogSalesModelRowDto;

import java.util.ArrayList;
import java.util.List;

public final class CatalogSalesSupport {

	private CatalogSalesSupport() {
	}

	public static List<CatalogSalesModelRowDto> toModelRows(List<Object[]> rows) {
		List<CatalogSalesModelRowDto> out = new ArrayList<>(rows.size());
		for (Object[] row : rows) {
			Integer sid = row[0] instanceof Integer i ? i : ((Number) row[0]).intValue();
			String model = row[1] != null ? String.valueOf(row[1]) : "";
			String brand = row[2] != null ? String.valueOf(row[2]) : "";
			long cnt = row[3] instanceof Long l ? l : ((Number) row[3]).longValue();
			out.add(CatalogSalesModelRowDto.builder()
					.subcategoryId(sid)
					.modelName(model)
					.brandName(brand)
					.soldCount(cnt)
					.build());
		}
		return out;
	}

	public static List<CatalogSalesBrandRowDto> toBrandRows(List<Object[]> rows) {
		List<CatalogSalesBrandRowDto> out = new ArrayList<>(rows.size());
		for (Object[] row : rows) {
			Integer cid = row[0] instanceof Integer i ? i : ((Number) row[0]).intValue();
			String name = row[1] != null ? String.valueOf(row[1]) : "";
			long cnt = row[2] instanceof Long l ? l : ((Number) row[2]).longValue();
			out.add(CatalogSalesBrandRowDto.builder()
					.categoryId(cid)
					.brandName(name)
					.soldCount(cnt)
					.build());
		}
		return out;
	}
}
