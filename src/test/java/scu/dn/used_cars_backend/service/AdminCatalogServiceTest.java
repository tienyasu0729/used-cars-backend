package scu.dn.used_cars_backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import scu.dn.used_cars_backend.entity.VehicleFuelType;
import scu.dn.used_cars_backend.entity.VehicleTransmission;
import scu.dn.used_cars_backend.repository.CategoryRepository;
import scu.dn.used_cars_backend.repository.SubcategoryRepository;
import scu.dn.used_cars_backend.repository.VehicleFuelTypeRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.repository.VehicleTransmissionRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private SubcategoryRepository subcategoryRepository;

	@Mock
	private VehicleRepository vehicleRepository;

	@Mock
	private VehicleFuelTypeRepository vehicleFuelTypeRepository;

	@Mock
	private VehicleTransmissionRepository vehicleTransmissionRepository;

	@InjectMocks
	private AdminCatalogService adminCatalogService;

	@Test
	void listFuelTypes_backfills_distinct_trimmed_labels_when_catalog_empty() {
		when(vehicleFuelTypeRepository.count()).thenReturn(0L);
		when(vehicleRepository.findDistinctActiveFuelLabels()).thenReturn(List.of(" Máy xăng ", "Máy dầu", "máy xăng"));
		when(vehicleFuelTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
				fuelType(1, "Máy xăng", "active"),
				fuelType(2, "Máy dầu", "active")));
		when(vehicleRepository.countActiveByFuelLabel("Máy xăng")).thenReturn(86L);
		when(vehicleRepository.countActiveByFuelLabel("Máy dầu")).thenReturn(12L);

		var rows = adminCatalogService.listFuelTypes();

		ArgumentCaptor<List<VehicleFuelType>> savedCaptor = ArgumentCaptor.forClass(List.class);
		verify(vehicleFuelTypeRepository).saveAll(savedCaptor.capture());
		List<VehicleFuelType> saved = savedCaptor.getValue();
		assertThat(saved).extracting(VehicleFuelType::getName).containsExactly("Máy xăng", "Máy dầu");
		assertThat(saved).extracting(VehicleFuelType::getStatus).containsOnly("active");
		assertThat(rows).extracting("name").containsExactly("Máy xăng", "Máy dầu");
		assertThat(rows).extracting("vehicleCount").containsExactly(86L, 12L);
	}

	@Test
	void listFuelTypes_skips_backfill_when_catalog_already_has_data() {
		when(vehicleFuelTypeRepository.count()).thenReturn(1L);
		when(vehicleFuelTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(fuelType(7, "Hybrid", "active")));
		when(vehicleRepository.countActiveByFuelLabel("Hybrid")).thenReturn(3L);

		var rows = adminCatalogService.listFuelTypes();

		verify(vehicleRepository, never()).findDistinctActiveFuelLabels();
		verify(vehicleFuelTypeRepository, never()).saveAll(anyList());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getName()).isEqualTo("Hybrid");
		assertThat(rows.get(0).getVehicleCount()).isEqualTo(3L);
	}

	@Test
	void listTransmissions_backfills_distinct_trimmed_labels_when_catalog_empty() {
		when(vehicleTransmissionRepository.count()).thenReturn(0L);
		when(vehicleRepository.findDistinctActiveTransmissionLabels()).thenReturn(List.of(" Số tự động ", "Số sàn", "số tự động"));
		when(vehicleTransmissionRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
				transmission(1, "Số tự động", "active"),
				transmission(2, "Số sàn", "active")));
		when(vehicleRepository.countActiveByTransmissionLabel("Số tự động")).thenReturn(77L);
		when(vehicleRepository.countActiveByTransmissionLabel("Số sàn")).thenReturn(23L);

		var rows = adminCatalogService.listTransmissions();

		ArgumentCaptor<List<VehicleTransmission>> savedCaptor = ArgumentCaptor.forClass(List.class);
		verify(vehicleTransmissionRepository).saveAll(savedCaptor.capture());
		List<VehicleTransmission> saved = savedCaptor.getValue();
		assertThat(saved).extracting(VehicleTransmission::getName).containsExactly("Số tự động", "Số sàn");
		assertThat(saved).extracting(VehicleTransmission::getStatus).containsOnly("active");
		assertThat(rows).extracting("name").containsExactly("Số tự động", "Số sàn");
		assertThat(rows).extracting("vehicleCount").containsExactly(77L, 23L);
	}

	private static VehicleFuelType fuelType(int id, String name, String status) {
		VehicleFuelType entity = new VehicleFuelType();
		entity.setId(id);
		entity.setName(name);
		entity.setStatus(status);
		return entity;
	}

	private static VehicleTransmission transmission(int id, String name, String status) {
		VehicleTransmission entity = new VehicleTransmission();
		entity.setId(id);
		entity.setName(name);
		entity.setStatus(status);
		return entity;
	}
}
