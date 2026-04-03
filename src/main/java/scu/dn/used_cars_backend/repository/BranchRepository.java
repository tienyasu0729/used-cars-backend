package scu.dn.used_cars_backend.repository;

// Truy vấn Branches — load kèm manager để kiểm tra BranchManager.

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Integer> {

	List<Branch> findAllByDeletedFalseOrderByIdAsc();

	long countByDeletedFalse();

	@EntityGraph(attributePaths = "manager")
	@Query("select b from Branch b where b.id = :id and b.deleted = false")
	Optional<Branch> findActiveByIdWithManager(@Param("id") Integer id);

	Optional<Branch> findByIdAndDeletedFalse(Integer id);

	Optional<Branch> findFirstByManager_IdAndDeletedFalse(Long managerId);

	/** Một user có thể là manager của một chi nhánh (hoặc nhiều nếu dữ liệu cho phép). */
	List<Branch> findAllByManager_IdAndDeletedFalse(Long managerId);

	boolean existsByIdAndDeletedFalseAndManager_Id(Integer id, Long managerId);

}
