package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogBrandRowDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogBrandsPageDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogModelRowDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogModelsPageDto;
import scu.dn.used_cars_backend.dto.admin.AdminCatalogTypedOptionDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogBrandRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogModelRequest;
import scu.dn.used_cars_backend.dto.admin.CreateAdminCatalogOptionRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogBrandRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogModelRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminCatalogTypedOptionRequest;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.VehicleFuelType;
import scu.dn.used_cars_backend.entity.VehicleTransmission;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleFuelTypeRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.repository.VehicleTransmissionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

	private final CategoryRepository categoryRepository;
	private final SubcategoryRepository subcategoryRepository;
	private final VehicleRepository vehicleRepository;
	private final VehicleFuelTypeRepository vehicleFuelTypeRepository;
	private final VehicleTransmissionRepository vehicleTransmissionRepository;

	@Transactional(readOnly = true)
	public AdminCatalogBrandsPageDto pageBrands(String q, int page, int size) {
		String qq = normalizeQ(q);
		int sz = Math.max(1, Math.min(size, 100));
		int pg = Math.max(0, page);
		Pageable p = PageRequest.of(pg, sz, Sort.by(Sort.Direction.ASC, "name"));
		Page<Category> result = categoryRepository.searchPage(qq, p);
		Map<Integer, Long> counts = toCountMap(vehicleRepository.countActiveByCategoryId());
		List<AdminCatalogBrandRowDto> rows = new ArrayList<>();
		for (Category c : result.getContent()) {
			rows.add(toBrandRow(c, counts.getOrDefault(c.getId(), 0L)));
		}
		return AdminCatalogBrandsPageDto.builder()
				.content(rows)
				.meta(PageMetaDto.builder()
						.page(result.getNumber())
						.size(result.getSize())
						.totalElements(result.getTotalElements())
						.totalPages(result.getTotalPages())
						.build())
				.build();
	}

	@Transactional(readOnly = true)
	public AdminCatalogModelsPageDto pageModels(String q, Integer categoryId, int page, int size) {
		String qq = normalizeQ(q);
		int sz = Math.max(1, Math.min(size, 100));
		int pg = Math.max(0, page);
		Pageable p = PageRequest.of(pg, sz, Sort.by(Sort.Direction.ASC, "category.id").and(Sort.by("name")));
		Page<Subcategory> result = subcategoryRepository.searchPage(qq, categoryId, p);
		Map<Integer, Long> counts = toCountMap(vehicleRepository.countActiveBySubcategoryId());
		List<AdminCatalogModelRowDto> rows = new ArrayList<>();
		for (Subcategory s : result.getContent()) {
			rows.add(toModelRow(s, counts.getOrDefault(s.getId(), 0L)));
		}
		return AdminCatalogModelsPageDto.builder()
				.content(rows)
				.meta(PageMetaDto.builder()
						.page(result.getNumber())
						.size(result.getSize())
						.totalElements(result.getTotalElements())
						.totalPages(result.getTotalPages())
						.build())
				.build();
	}

	@Transactional
	public AdminCatalogBrandRowDto createBrand(CreateAdminCatalogBrandRequest req) {
		String name = req.getName().trim();
		if (categoryRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên hãng đã tồn tại.");
		}
		int nextId = categoryRepository.findMaxId().orElse(0) + 1;
		Category c = new Category();
		c.setId(nextId);
		c.setName(name);
		c.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		categoryRepository.save(c);
		return toBrandRow(c, 0L);
	}

	@Transactional
	public void updateBrand(int id, UpdateAdminCatalogBrandRequest req) {
		Category c = categoryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Không tìm thấy hãng."));
		String name = req.getName().trim();
		if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên hãng đã tồn tại.");
		}
		c.setName(name);
		c.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		categoryRepository.save(c);
	}

	@Transactional
	public AdminCatalogModelRowDto createModel(CreateAdminCatalogModelRequest req) {
		Category cat = categoryRepository.findById(req.getCategoryId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_FOUND, "Không tìm thấy hãng."));
		String name = req.getName().trim();
		if (subcategoryRepository.existsByNameIgnoreCaseAndCategory_Id(name, cat.getId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Dòng xe cùng tên đã tồn tại trong hãng này.");
		}
		int nextId = subcategoryRepository.findMaxId().orElse(0) + 1;
		Subcategory s = new Subcategory();
		s.setId(nextId);
		s.setCategory(cat);
		s.setName(name);
		s.setNameNormalized(name.toLowerCase(Locale.ROOT));
		s.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		subcategoryRepository.save(s);
		return toModelRow(s, 0L);
	}

	@Transactional
	public void updateModel(int id, UpdateAdminCatalogModelRequest req) {
		Subcategory s = subcategoryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.MODEL_NOT_FOUND, "Không tìm thấy dòng xe."));
		String name = req.getName().trim();
		Integer cid = s.getCategory().getId();
		if (subcategoryRepository.existsByNameIgnoreCaseAndCategory_IdAndIdNot(name, cid, id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Dòng xe cùng tên đã tồn tại trong hãng này.");
		}
		s.setName(name);
		s.setNameNormalized(name.toLowerCase(Locale.ROOT));
		s.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		subcategoryRepository.save(s);
	}

	@Transactional(readOnly = true)
	public List<AdminCatalogTypedOptionDto> listFuelTypes() {
		List<AdminCatalogTypedOptionDto> out = new ArrayList<>();
		for (VehicleFuelType f : vehicleFuelTypeRepository.findAllByOrderByNameAsc()) {
			long c = vehicleRepository.countActiveByFuelLabel(f.getName());
			out.add(AdminCatalogTypedOptionDto.builder()
					.id(f.getId())
					.name(f.getName())
					.status(f.getStatus())
					.vehicleCount(c)
					.build());
		}
		return out;
	}

	@Transactional(readOnly = true)
	public List<AdminCatalogTypedOptionDto> listTransmissions() {
		List<AdminCatalogTypedOptionDto> out = new ArrayList<>();
		for (VehicleTransmission t : vehicleTransmissionRepository.findAllByOrderByNameAsc()) {
			long c = vehicleRepository.countActiveByTransmissionLabel(t.getName());
			out.add(AdminCatalogTypedOptionDto.builder()
					.id(t.getId())
					.name(t.getName())
					.status(t.getStatus())
					.vehicleCount(c)
					.build());
		}
		return out;
	}

	@Transactional
	public AdminCatalogTypedOptionDto createFuelType(CreateAdminCatalogOptionRequest req) {
		String name = req.getName().trim();
		if (name.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên không được để trống.");
		}
		if (vehicleFuelTypeRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Loại nhiên liệu đã tồn tại.");
		}
		VehicleFuelType f = new VehicleFuelType();
		f.setName(name);
		f.setStatus("active");
		vehicleFuelTypeRepository.save(f);
		return AdminCatalogTypedOptionDto.builder()
				.id(f.getId())
				.name(f.getName())
				.status(f.getStatus())
				.vehicleCount(0L)
				.build();
	}

	@Transactional
	public AdminCatalogTypedOptionDto createTransmission(CreateAdminCatalogOptionRequest req) {
		String name = req.getName().trim();
		if (name.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên không được để trống.");
		}
		if (vehicleTransmissionRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Loại hộp số đã tồn tại.");
		}
		VehicleTransmission t = new VehicleTransmission();
		t.setName(name);
		t.setStatus("active");
		vehicleTransmissionRepository.save(t);
		return AdminCatalogTypedOptionDto.builder()
				.id(t.getId())
				.name(t.getName())
				.status(t.getStatus())
				.vehicleCount(0L)
				.build();
	}

	@Transactional
	public void updateFuelType(int id, UpdateAdminCatalogTypedOptionRequest req) {
		VehicleFuelType f = vehicleFuelTypeRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại nhiên liệu."));
		String name = req.getName().trim();
		if (name.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên không được để trống.");
		}
		if (vehicleFuelTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên loại nhiên liệu đã tồn tại.");
		}
		f.setName(name);
		f.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		vehicleFuelTypeRepository.save(f);
	}

	@Transactional
	public void updateTransmission(int id, UpdateAdminCatalogTypedOptionRequest req) {
		VehicleTransmission t = vehicleTransmissionRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại hộp số."));
		String name = req.getName().trim();
		if (name.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên không được để trống.");
		}
		if (vehicleTransmissionRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên loại hộp số đã tồn tại.");
		}
		t.setName(name);
		t.setStatus(req.getStatus().toLowerCase(Locale.ROOT));
		vehicleTransmissionRepository.save(t);
	}

	private static String normalizeQ(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		return q.trim();
	}

	private static Map<Integer, Long> toCountMap(List<Object[]> rows) {
		Map<Integer, Long> m = new HashMap<>();
		for (Object[] row : rows) {
			m.put((Integer) row[0], (Long) row[1]);
		}
		return m;
	}

	private static AdminCatalogBrandRowDto toBrandRow(Category c, long vehicleCount) {
		return AdminCatalogBrandRowDto.builder()
				.id(String.valueOf(c.getId()))
				.name(c.getName())
				.slug(slugify(c.getName()))
				.vehicleCount(vehicleCount)
				.status(c.getStatus())
				.build();
	}

	private static AdminCatalogModelRowDto toModelRow(Subcategory s, long vehicleCount) {
		return AdminCatalogModelRowDto.builder()
				.id(String.valueOf(s.getId()))
				.name(s.getName())
				.categoryId(String.valueOf(s.getCategory().getId()))
				.vehicleCount(vehicleCount)
				.status(s.getStatus())
				.build();
	}

	private static String slugify(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
	}
}
