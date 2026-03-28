package scu.dn.used_cars_backend.repository;

// Truy vấn Subcategories — kiểm tra dòng xe thuộc đúng category.

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.Subcategory;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Integer> {

	Optional<Subcategory> findByIdAndCategory_Id(Integer id, Integer categoryId);

	List<Subcategory> findByCategory_IdAndStatusOrderByNameAsc(Integer categoryId, String status);

}
