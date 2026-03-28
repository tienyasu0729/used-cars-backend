package scu.dn.used_cars_backend.repository;

// Truy vấn Categories; findByIdForUpdate dùng khi sinh listing_id (khóa dòng category).

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

	List<Category> findByStatusOrderByNameAsc(String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Category c where c.id = :id")
	Optional<Category> findByIdForUpdate(@Param("id") Integer id);

}
