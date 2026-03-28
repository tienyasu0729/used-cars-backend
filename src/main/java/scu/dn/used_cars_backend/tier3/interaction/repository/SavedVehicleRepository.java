package scu.dn.used_cars_backend.tier3.interaction.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.tier3.interaction.entity.SavedVehicle;
import scu.dn.used_cars_backend.tier3.interaction.entity.SavedVehicleId;

import java.util.List;

public interface SavedVehicleRepository extends JpaRepository<SavedVehicle, SavedVehicleId> {

	boolean existsByUser_IdAndVehicle_Id(Long userId, Long vehicleId);

	@EntityGraph(attributePaths = { "vehicle", "vehicle.images" })
	@Query("select sv from SavedVehicle sv where sv.id.userId = :userId order by sv.savedAt desc")
	List<SavedVehicle> findAllSavedForUser(@Param("userId") Long userId);
}
