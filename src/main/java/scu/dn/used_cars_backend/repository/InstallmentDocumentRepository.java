package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import scu.dn.used_cars_backend.entity.InstallmentDocument;

import java.util.List;

@Repository
public interface InstallmentDocumentRepository extends JpaRepository<InstallmentDocument, Long> {
	List<InstallmentDocument> findByApplicationId(Long applicationId);
	long countByApplicationIdAndDocumentTypeIgnoreCase(Long applicationId, String documentType);
}
