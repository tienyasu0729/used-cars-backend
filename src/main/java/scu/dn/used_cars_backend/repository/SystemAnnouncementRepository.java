package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.SystemAnnouncement;

public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Integer> {

	Page<SystemAnnouncement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
