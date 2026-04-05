package scu.dn.used_cars_backend.repository;

// Truy vấn bảng Vehicles / listing_id — query JPQL giữ nguyên, không đổi tên method khi refactor.

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	@EntityGraph(attributePaths = { "branch" })
	@Query("select v from Vehicle v where v.id in :ids")
	List<Vehicle> findAllByIdInWithBranch(@Param("ids") Collection<Long> ids);

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
	@Query("select v from Vehicle v where v.id in :ids and v.deleted = false and v.status <> 'Hidden'")
	List<Vehicle> findPublicByIds(@Param("ids") Collection<Long> ids);

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
			and (:vehicleStatus is null or v.status = :vehicleStatus)
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
			@Param("vehicleStatus") String vehicleStatus,
			Pageable pageable);

	@Query("select v from Vehicle v where v.id = :id and v.deleted = false and v.status = :status")
	Optional<Vehicle> findAvailableForBooking(@Param("id") Long id, @Param("status") String status);

	long countByBranch_IdAndDeletedFalse(int branchId);

	@Query("""
			select count(v) from Vehicle v
			where v.branch.id = :branchId
			and v.deleted = false
			and v.status = :status
			""")
	long countByBranchIdAndDeletedFalseAndStatus(@Param("branchId") int branchId, @Param("status") String status);

	long countByDeletedFalse();

	long countByDeletedFalseAndStatus(String status);

	@Query("""
			select v.category.id, count(v) from Vehicle v
			where v.deleted = false
			group by v.category.id
			""")
	List<Object[]> countActiveByCategoryId();

	@Query("""
			select v.subcategory.id, count(v) from Vehicle v
			where v.deleted = false
			group by v.subcategory.id
			""")
	List<Object[]> countActiveBySubcategoryId();

	@Query("""
			select v.category.id, v.category.name, count(v) from Vehicle v
			where v.deleted = false and v.status = 'Sold'
			and (:branchId is null or v.branch.id = :branchId)
			group by v.category.id, v.category.name
			""")
	List<Object[]> countSoldByCategory(@Param("branchId") Integer branchId);

	@Query("""
			select v.subcategory.id, v.subcategory.name, v.category.name, count(v) from Vehicle v
			where v.deleted = false and v.status = 'Sold'
			and (:branchId is null or v.branch.id = :branchId)
			group by v.subcategory.id, v.subcategory.name, v.category.name
			order by count(v) desc
			""")
	List<Object[]> countSoldBySubcategory(@Param("branchId") Integer branchId);

	@Query("""
			select count(v) from Vehicle v
			where v.deleted = false and lower(trim(coalesce(v.fuel, ''))) = lower(trim(:label))
			""")
	long countActiveByFuelLabel(@Param("label") String label);

	@Query("""
			select count(v) from Vehicle v
			where v.deleted = false and lower(trim(coalesce(v.transmission, ''))) = lower(trim(:label))
			""")
	long countActiveByTransmissionLabel(@Param("label") String label);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.id = :id and v.deleted = false")
	Optional<Vehicle> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);

	/** Lock xe bất kể deleted — dùng khi staff/manager tạo deposit (xe ẩn vẫn tracked). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.id = :id")
	Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

}
