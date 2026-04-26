package scu.dn.used_cars_backend.service.vehicle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleImage;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VehicleReadPortImpl implements VehicleReadPort {

	private final VehicleRepository vehicleRepository;

	@Override
	public boolean existsForCustomerSave(long vehicleId) {
		return vehicleRepository.existsByIdAndDeletedFalse(vehicleId);
	}

	@Override
	public List<InteractionVehicleSnapshot> findSnapshotsByIdsPreserveOrder(List<Long> orderedIds) {
		if (orderedIds == null || orderedIds.isEmpty()) {
			return List.of();
		}
		List<Vehicle> loaded = vehicleRepository.findAllByIdInWithImages(new HashSet<>(orderedIds));
		Map<Long, Vehicle> byId = new HashMap<>();
		for (Vehicle v : loaded) {
			byId.put(v.getId(), v);
		}
		List<InteractionVehicleSnapshot> out = new ArrayList<>();
		for (Long id : orderedIds) {
			Vehicle v = byId.get(id);
			if (v != null) {
				out.add(toSnapshot(v));
			}
		}
		return out;
	}

	private static InteractionVehicleSnapshot toSnapshot(Vehicle v) {
		return new InteractionVehicleSnapshot(v.getId(), v.getListingId(), v.getTitle(), v.getPrice(), v.getStatus(),
				pickPrimaryImageUrl(v), v.isDeleted());
	}

	private static String pickPrimaryImageUrl(Vehicle v) {
		for (VehicleImage i : v.getImages()) {
			if (i.isPrimaryImage()) {
				return i.getImageUrl();
			}
		}
		if (v.getImages().isEmpty()) {
			return null;
		}
		return v.getImages().get(0).getImageUrl();
	}

	@Override
	public Vehicle vehicleFkReference(long vehicleId) {
		return vehicleRepository.getReferenceById(vehicleId);
	}
}
