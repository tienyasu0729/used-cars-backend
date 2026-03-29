package scu.dn.used_cars_backend.interaction.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.interaction.entity.VehicleViewHistory;

import java.util.List;

public interface VehicleViewHistoryRepository extends JpaRepository<VehicleViewHistory, Long> {

	@Query("select v from VehicleViewHistory v where v.userId = :userId order by v.viewedAt desc")
	List<VehicleViewHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select v from VehicleViewHistory v where v.guestId = :guestId and v.userId is null order by v.viewedAt desc")
	List<VehicleViewHistory> findRecentByGuestOnly(@Param("guestId") String guestId, Pageable pageable);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update VehicleViewHistory v set v.userId = :userId where v.guestId = :guestId and v.userId is null")
	int updateUserIdForGuestMerge(@Param("userId") Long userId, @Param("guestId") String guestId);
}
