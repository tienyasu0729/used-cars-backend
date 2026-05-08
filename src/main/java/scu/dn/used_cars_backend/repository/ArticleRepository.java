package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.Article;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

	Optional<Article> findBySlugAndDeletedFalse(String slug);

	boolean existsBySlug(String slug);

	@Modifying
	@Query("UPDATE Article a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
	void incrementViewCount(@Param("id") Long id);

	Page<Article> findByDeletedFalse(Pageable pageable);
}
