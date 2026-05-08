package scu.dn.used_cars_backend.service;

// Service đọc catalog (hãng / dòng xe) từ bảng Categories, Subcategories — chỉ xem, không sửa.

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.catalog.CatalogCategoryDto;
import scu.dn.used_cars_backend.dto.catalog.CatalogSubcategoryDto;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.VehicleFuelType;
import scu.dn.used_cars_backend.entity.VehicleTransmission;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleFuelTypeRepository;
import scu.dn.used_cars_backend.repository.VehicleTransmissionRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

	private static final String ACTIVE = "active";

	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final VehicleFuelTypeRepository vehicleFuelTypeRepository;
	private final VehicleTransmissionRepository vehicleTransmissionRepository;

	@Transactional(readOnly = true)
	public List<CatalogCategoryDto> listCategories() {
		List<CatalogCategoryDto> out = new ArrayList<>();
		for (Category c : categoryRepository.findByStatusOrderByNameAsc(ACTIVE)) {
			CatalogCategoryDto d = new CatalogCategoryDto();
			d.setId(c.getId());
			d.setName(c.getName());
			d.setStatus(c.getStatus());
			out.add(d);
		}
		return out;
	}

	@Transactional(readOnly = true)
	public List<CatalogSubcategoryDto> listSubcategories(Integer categoryId) {
		List<CatalogSubcategoryDto> out = new ArrayList<>();
		for (Subcategory s : subcategoryRepository.findByCategory_IdAndStatusOrderByNameAsc(categoryId, ACTIVE)) {
			CatalogSubcategoryDto d = new CatalogSubcategoryDto();
			d.setId(s.getId());
			d.setCategoryId(s.getCategory().getId());
			d.setName(s.getName());
			d.setStatus(s.getStatus());
			out.add(d);
		}
		return out;
	}

	@Transactional(readOnly = true)
	public List<String> listActiveFuelTypeNames() {
		List<String> out = new ArrayList<>();
		for (VehicleFuelType f : vehicleFuelTypeRepository.findByStatusIgnoreCaseOrderByNameAsc(ACTIVE)) {
			if (f.getName() != null && !f.getName().isBlank()) {
				out.add(f.getName().trim());
			}
		}
		return out;
	}

	@Transactional(readOnly = true)
	public List<String> listActiveTransmissionNames() {
		List<String> out = new ArrayList<>();
		for (VehicleTransmission t : vehicleTransmissionRepository.findByStatusIgnoreCaseOrderByNameAsc(ACTIVE)) {
			if (t.getName() != null && !t.getName().isBlank()) {
				out.add(t.getName().trim());
			}
		}
		return out;
	}

}
