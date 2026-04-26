package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.ArticleCategory;

import java.util.List;
import java.util.Optional;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {

	List<ArticleCategory> findByActiveTrueOrderBySortOrderAsc();

	Optional<ArticleCategory> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsByName(String name);
}
