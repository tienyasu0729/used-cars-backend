package scu.dn.used_cars_backend.repository;

// Truy vấn bảng Vehicles / listing_id — query JPQL giữ nguyên, không đổi tên method khi refactor.

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	@Query("select v.listingId from Vehicle v where v.listingId like concat(:prefix, '%')")
	List<String> findListingIdsByPrefix(@Param("prefix") String prefix);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "images" })
	@Query("""
			select v from Vehicle v
			where v.deleted = false
			and v.status <> 'Hidden'
			and (:categoryId is null or v.category.id = :categoryId)
			and (:minPrice is null or (v.price is not null and v.price >= :minPrice))
			and (:maxPrice is null or (v.price is not null and v.price <= :maxPrice))
			""")
	Page<Vehicle> findPublicPage(@Param("categoryId") Integer categoryId, @Param("minPrice") java.math.BigDecimal minPrice,
			@Param("maxPrice") java.math.BigDecimal maxPrice, Pageable pageable);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("select v from Vehicle v where v.id = :id and v.deleted = false and v.status <> 'Hidden'")
	Optional<Vehicle> findPublicDetailById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("select v from Vehicle v where v.id = :id and v.deleted = false")
	Optional<Vehicle> findManagedDetailById(@Param("id") Long id);

}
