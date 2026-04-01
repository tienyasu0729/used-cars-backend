package scu.dn.used_cars_backend.repository;

// Truy vấn bảng Vehicles / listing_id — query JPQL giữ nguyên, không đổi tên method khi refactor.

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	boolean existsByListingId(String listingId);

	boolean existsByIdAndDeletedFalse(Long id);

	@EntityGraph(attributePaths = { "images" })
	@Query("select v from Vehicle v where v.id in :ids")
	List<Vehicle> findAllByIdInWithImages(@Param("ids") Collection<Long> ids);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "images" })
	@Query("""
			select v from Vehicle v
			where v.deleted = false
			and v.status <> 'Hidden'
			and (:keyword is null or lower(v.title) like lower(concat('%', :keyword, '%')))
			and (:categoryId is null or v.category.id = :categoryId)
			and (:subcategoryId is null or v.subcategory.id = :subcategoryId)
			and (:minPrice is null or (v.price is not null and v.price >= :minPrice))
			and (:maxPrice is null or (v.price is not null and v.price <= :maxPrice))
			and (:yearMin is null or (v.year is not null and v.year >= :yearMin))
			and (:yearMax is null or (v.year is not null and v.year <= :yearMax))
			and (:transmission is null or v.transmission = :transmission)
			and (:branchId is null or v.branch.id = :branchId)
			""")
	Page<Vehicle> findPublicPage(@Param("keyword") String keyword,
			@Param("categoryId") Integer categoryId,
			@Param("subcategoryId") Integer subcategoryId,
			@Param("minPrice") java.math.BigDecimal minPrice,
			@Param("maxPrice") java.math.BigDecimal maxPrice,
			@Param("yearMin") Integer yearMin,
			@Param("yearMax") Integer yearMax,
			@Param("transmission") String transmission,
			@Param("branchId") Integer branchId,
			Pageable pageable);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("select v from Vehicle v where v.id = :id and v.deleted = false and v.status <> 'Hidden'")
	Optional<Vehicle> findPublicDetailById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("select v from Vehicle v where v.id = :id")
	Optional<Vehicle> findManagedDetailById(@Param("id") Long id);

	/**
	 * Danh sách xe cho manager: gồm cả is_deleted=1 (đã ẩn khỏi trang công khai), chỉ giới hạn branchIds.
	 */
	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "images" })
	@Query("""
			select v from Vehicle v
			where v.branch.id in :branchIds
			and (:categoryId is null or v.category.id = :categoryId)
			and (:subcategoryId is null or v.subcategory.id = :subcategoryId)
			and (:minPrice is null or (v.price is not null and v.price >= :minPrice))
			and (:maxPrice is null or (v.price is not null and v.price <= :maxPrice))
			and (:yearMin is null or (v.year is not null and v.year >= :yearMin))
			and (:yearMax is null or (v.year is not null and v.year <= :yearMax))
			and (:transmission is null or v.transmission = :transmission)
			and (:branchId is null or v.branch.id = :branchId)
			""")
	Page<Vehicle> findManagedPage(@Param("branchIds") Collection<Integer> branchIds,
			@Param("categoryId") Integer categoryId,
			@Param("subcategoryId") Integer subcategoryId,
			@Param("minPrice") java.math.BigDecimal minPrice,
			@Param("maxPrice") java.math.BigDecimal maxPrice,
			@Param("yearMin") Integer yearMin,
			@Param("yearMax") Integer yearMax,
			@Param("transmission") String transmission,
			@Param("branchId") Integer branchId,
			Pageable pageable);

	@Query("select v from Vehicle v where v.id = :id and v.deleted = false and v.status = 'Available'")
	Optional<Vehicle> findAvailableForBooking(@Param("id") Long id);

}
