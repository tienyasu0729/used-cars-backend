package scu.dn.used_cars_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Category;
import scu.dn.used_cars_backend.entity.Subcategory;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleImageRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServicePublicDetailTokenTest {

	@Mock
	private VehicleRepository vehicleRepository;
	@Mock
	private VehicleImageRepository vehicleImageRepository;
	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private SubcategoryRepository subcategoryRepository;
	@Mock
	private BranchRepository branchRepository;
	@Mock
	private StaffAssignmentRepository staffAssignmentRepository;
	@Mock
	private DepositService depositService;
	@Mock
	private DepositRepository depositRepository;
	@Mock
	private EmailNotificationService emailNotificationService;

	private VehicleService vehicleService;

	@BeforeEach
	void setUp() {
		vehicleService = new VehicleService(
				vehicleRepository,
				vehicleImageRepository,
				categoryRepository,
				subcategoryRepository,
				branchRepository,
				staffAssignmentRepository,
				new ConcurrentMapCacheManager("vehicleList", "vehicleDetail"),
				depositService,
				depositRepository,
				emailNotificationService);
		when(depositRepository.countByVehicleIdAndStatusIn(anyLong(), any())).thenReturn(0L);
	}

	@Test
	void getPublicDetailByToken_prefersInternalId() {
		Vehicle vehicle = vehicle(2754L, "23418271");
		when(vehicleRepository.findPublicDetailById(2754L)).thenReturn(Optional.of(vehicle));

		VehicleDetailDto dto = vehicleService.getPublicDetailByToken("2754");

		assertThat(dto).isNotNull();
		assertThat(dto.getId()).isEqualTo(2754L);
		assertThat(dto.getListingId()).isEqualTo("23418271");
		verify(vehicleRepository).findPublicDetailById(2754L);
		verify(vehicleRepository, never()).findPublicDetailByListingId(any());
	}

	@Test
	void getPublicDetailByToken_fallsBackToListingId() {
		Vehicle vehicle = vehicle(2754L, "23418271");
		when(vehicleRepository.findPublicDetailById(23418271L)).thenReturn(Optional.empty());
		when(vehicleRepository.findPublicDetailByListingId("23418271")).thenReturn(Optional.of(vehicle));

		VehicleDetailDto dto = vehicleService.getPublicDetailByToken("23418271");

		assertThat(dto).isNotNull();
		assertThat(dto.getId()).isEqualTo(2754L);
		assertThat(dto.getListingId()).isEqualTo("23418271");
		verify(vehicleRepository).findPublicDetailById(23418271L);
		verify(vehicleRepository).findPublicDetailByListingId("23418271");
	}

	private static Vehicle vehicle(Long id, String listingId) {
		Category category = new Category();
		category.setId(1);
		category.setName("Volvo");

		Subcategory subcategory = new Subcategory();
		subcategory.setId(2);
		subcategory.setName("XC60");
		subcategory.setCategory(category);

		Branch branch = new Branch();
		branch.setId(3);
		branch.setName("Đà Nẵng");

		Vehicle vehicle = new Vehicle();
		vehicle.setId(id);
		vehicle.setListingId(listingId);
		vehicle.setCategory(category);
		vehicle.setSubcategory(subcategory);
		vehicle.setBranch(branch);
		vehicle.setTitle("Volvo XC60 B6 Ultimate Bright 2023");
		vehicle.setPrice(new BigDecimal("1559000000"));
		vehicle.setDescription("demo");
		vehicle.setYear(2023);
		vehicle.setFuel("Hybrid");
		vehicle.setTransmission("Số tự động");
		vehicle.setMileage(40000);
		vehicle.setStatus("Available");
		vehicle.setDeleted(false);
		vehicle.setImages(List.of());
		return vehicle;
	}
}
