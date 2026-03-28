package scu.dn.used_cars_backend.service;

// Service đọc catalog (hãng / dòng xe) từ bảng Categories, Subcategories — chỉ xem, không sửa.

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.catalog.CatalogCategoryDto;
import scu.dn.used_cars_backend.dto.catalog.CatalogSubcategoryDto;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

	private static final String ACTIVE = "active";

	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;

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

}
