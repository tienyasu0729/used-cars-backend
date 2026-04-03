package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.InAppNotification;

import java.util.List;
import java.util.Optional;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long>, JpaSpecificationExecutor<InAppNotification> {

	Page<InAppNotification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Page<InAppNotification> findByUser_IdAndNotificationReadOrderByCreatedAtDesc(Long userId, boolean notificationRead,
			Pageable pageable);

	long countByUser_IdAndNotificationReadFalse(Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update InAppNotification n set n.notificationRead = true where n.user.id = :userId and n.notificationRead = false")
	int markAllReadForUser(@Param("userId") Long userId);

	Optional<InAppNotification> findByIdAndUser_Id(Long id, Long userId);

	@Query("select distinct n.user.email from InAppNotification n where n.systemAnnouncementId = :sid")
	List<String> findDistinctUserEmailsForSystemAnnouncement(@Param("sid") Integer sid);
}
