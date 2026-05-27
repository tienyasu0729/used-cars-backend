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
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			and (:keyword is null
			  or lower(v.title) like lower(concat('%', :keyword, '%'))
			  or lower(v.category.name) like lower(concat('%', :keyword, '%'))
			  or lower(v.subcategory.name) like lower(concat('%', :keyword, '%')))
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
	@Query("""
			select v from Vehicle v
			where v.id in :ids
			and v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			""")
	List<Vehicle> findPublicByIds(@Param("ids") Collection<Long> ids);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("""
			select v from Vehicle v
			where v.id = :id
			and v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			""")
	Optional<Vehicle> findPublicDetailById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "category", "subcategory", "branch", "branch.manager", "images" })
	@Query("""
			select v from Vehicle v
			where v.listingId = :listingId
			and v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			""")
	Optional<Vehicle> findPublicDetailByListingId(@Param("listingId") String listingId);

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
			and (:keyword is null
			  or lower(v.title) like lower(concat('%', :keyword, '%'))
			  or lower(v.listingId) like lower(concat('%', :keyword, '%'))
			  or lower(v.category.name) like lower(concat('%', :keyword, '%'))
			  or lower(v.subcategory.name) like lower(concat('%', :keyword, '%'))
			  or lower(v.branch.name) like lower(concat('%', :keyword, '%')))
			and (:categoryId is null or v.category.id = :categoryId)
			and (:subcategoryId is null or v.subcategory.id = :subcategoryId)
			and (:minPrice is null or (v.price is not null and v.price >= :minPrice))
			and (:maxPrice is null or (v.price is not null and v.price <= :maxPrice))
			and (:yearMin is null or (v.year is not null and v.year >= :yearMin))
			and (:yearMax is null or (v.year is not null and v.year <= :yearMax))
			and (:transmission is null or v.transmission = :transmission)
			and (:branchId is null or v.branch.id = :branchId)
			and (:vehicleStatus is null or v.status = :vehicleStatus)
			and (:excludeStatus is null or v.status <> :excludeStatus)
			""")
	Page<Vehicle> findManagedPage(@Param("branchIds") Collection<Integer> branchIds,
			@Param("keyword") String keyword,
			@Param("categoryId") Integer categoryId,
			@Param("subcategoryId") Integer subcategoryId,
			@Param("minPrice") java.math.BigDecimal minPrice,
			@Param("maxPrice") java.math.BigDecimal maxPrice,
			@Param("yearMin") Integer yearMin,
			@Param("yearMax") Integer yearMax,
			@Param("transmission") String transmission,
			@Param("branchId") Integer branchId,
			@Param("vehicleStatus") String vehicleStatus,
			@Param("excludeStatus") String excludeStatus,
			Pageable pageable);

	@Query("""
			select v from Vehicle v
			where v.id = :id
			and v.deleted = false
			and v.status = :status
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			""")
	Optional<Vehicle> findAvailableForBooking(@Param("id") Long id, @Param("status") String status);

	@Query("select v.id from Vehicle v where v.branch.id = :branchId and v.deleted = false")
	List<Long> findIdsByBranchIdAndDeletedFalse(@Param("branchId") int branchId);

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

	@Query("""
			select distinct trim(v.fuel) from Vehicle v
			where v.deleted = false
			and v.fuel is not null
			and trim(v.fuel) <> ''
			order by trim(v.fuel) asc
			""")
	List<String> findDistinctActiveFuelLabels();

	@Query("""
			select distinct trim(v.transmission) from Vehicle v
			where v.deleted = false
			and v.transmission is not null
			and trim(v.transmission) <> ''
			order by trim(v.transmission) asc
			""")
	List<String> findDistinctActiveTransmissionLabels();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.id = :id and v.deleted = false")
	Optional<Vehicle> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);

	/** Lock xe bất kể deleted — dùng khi staff/manager tạo deposit (xe ẩn vẫn tracked). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.id = :id")
	Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

	/** Gợi ý title xe đang bán */
	@Query("""
			select distinct v.title from Vehicle v
			where v.deleted = false and v.status = 'Available'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			and lower(v.title) like lower(concat('%', :q, '%'))
			order by v.title asc
			""")
	List<String> findTitleSuggestions(@Param("q") String q, Pageable pageable);

	/** Lấy danh sách năm sản xuất distinct của xe đang bán */
	@Query("""
			select distinct v.year from Vehicle v
			where v.deleted = false and v.status = 'Available'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			and v.year is not null
			order by v.year desc
			""")
	List<Integer> findDistinctYears();

	@Query("""
			select distinct v.category.id from Vehicle v
			where v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			order by v.category.id asc
			""")
	List<Integer> findPublicCategoryIds();

	@Query("""
			select distinct v.category.id, v.subcategory.id from Vehicle v
			where v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			order by v.category.id asc, v.subcategory.id asc
			""")
	List<Object[]> findPublicCategorySubcategoryPairs();

	@Query("""
			select min(v.price), max(v.price) from Vehicle v
			where v.deleted = false
			and v.status <> 'Hidden'
			and v.status <> 'Sold'
			and v.status <> 'Reserved'
			and v.branch.deleted = false
			and lower(coalesce(v.branch.status, 'active')) = 'active'
			and v.price is not null
			""")
	Object[] findPublicPriceRange();

}
