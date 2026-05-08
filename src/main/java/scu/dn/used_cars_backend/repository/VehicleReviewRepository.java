package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.VehicleReview;

public interface VehicleReviewRepository extends JpaRepository<VehicleReview, Long>, JpaSpecificationExecutor<VehicleReview> {

	boolean existsByVehicleIdAndReviewerId(Long vehicleId, Long reviewerId);

	Page<VehicleReview> findByVehicleIdAndStatus(Long vehicleId, String status, Pageable pageable);

	@Query("SELECT AVG(r.rating) FROM VehicleReview r WHERE r.vehicle.id = :vehicleId AND r.status = 'approved'")
	Double findAverageRatingByVehicleId(@Param("vehicleId") Long vehicleId);

	@Query("SELECT COUNT(r) FROM VehicleReview r WHERE r.vehicle.id = :vehicleId AND r.status = 'approved'")
	long countApprovedByVehicleId(@Param("vehicleId") Long vehicleId);

	@Query("SELECT r.rating, COUNT(r) FROM VehicleReview r WHERE r.vehicle.id = :vehicleId AND r.status = 'approved' GROUP BY r.rating")
	java.util.List<Object[]> findRatingDistribution(@Param("vehicleId") Long vehicleId);

	Page<VehicleReview> findByVehicleId(Long vehicleId, Pageable pageable);
}
