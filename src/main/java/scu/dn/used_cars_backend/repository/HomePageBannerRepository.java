package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import scu.dn.used_cars_backend.entity.HomePageBanner;

import java.util.List;
import java.util.Optional;

public interface HomePageBannerRepository extends JpaRepository<HomePageBanner, Long> {

	List<HomePageBanner> findAllByOrderBySortOrderAscIdAsc();

	@Query("select max(h.sortOrder) from HomePageBanner h")
	Optional<Integer> findMaxSortOrder();
}
