package scu.dn.used_cars_backend.repository;

// Truy vấn Subcategories — kiểm tra dòng xe thuộc đúng category.

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Subcategory;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Integer> {

	Optional<Subcategory> findByIdAndCategory_Id(Integer id, Integer categoryId);

	List<Subcategory> findByCategory_IdAndStatusOrderByNameAsc(Integer categoryId, String status);

	List<Subcategory> findAllByOrderByCategory_IdAscNameAsc();

	boolean existsByNameIgnoreCaseAndCategory_Id(String name, Integer categoryId);

	boolean existsByNameIgnoreCaseAndCategory_IdAndIdNot(String name, Integer categoryId, Integer id);

	@Query("select max(s.id) from Subcategory s")
	Optional<Integer> findMaxId();

	@Query("""
			select s from Subcategory s
			where (:q is null or lower(s.name) like lower(concat('%', :q, '%')))
			and (:categoryId is null or s.category.id = :categoryId)
			""")
	Page<Subcategory> searchPage(@Param("q") String q, @Param("categoryId") Integer categoryId, Pageable pageable);

}
