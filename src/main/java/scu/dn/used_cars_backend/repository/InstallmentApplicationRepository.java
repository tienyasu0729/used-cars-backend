package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import scu.dn.used_cars_backend.entity.InstallmentApplication;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstallmentApplicationRepository extends JpaRepository<InstallmentApplication, Long>, JpaSpecificationExecutor<InstallmentApplication> {
	@Query("""
			select ia
			from InstallmentApplication ia
			left join fetch ia.vehicle
			where ia.customer.id = :customerId
			order by ia.createdAt desc
			""")
	List<InstallmentApplication> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

	@Query(
			value = """
					select ia
					from InstallmentApplication ia
					left join fetch ia.vehicle
					where ia.customer.id = :customerId
					and (:status is null or ia.status = :status)
					and (
						:q is null
						or str(ia.id) like concat('%', :q, '%')
						or lower(coalesce(ia.fullName, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.phoneNumber, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.identityNumber, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.vehicle.title, '')) like lower(concat('%', :q, '%'))
					)
					""",
			countQuery = """
					select count(ia)
					from InstallmentApplication ia
					where ia.customer.id = :customerId
					and (:status is null or ia.status = :status)
					and (
						:q is null
						or str(ia.id) like concat('%', :q, '%')
						or lower(coalesce(ia.fullName, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.phoneNumber, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.identityNumber, '')) like lower(concat('%', :q, '%'))
						or lower(coalesce(ia.vehicle.title, '')) like lower(concat('%', :q, '%'))
					)
					"""
	)
	Page<InstallmentApplication> searchMyApplications(
			@Param("customerId") Long customerId,
			@Param("status") InstallmentApplication.Status status,
			@Param("q") String q,
			Pageable pageable);
	List<InstallmentApplication> findByStatusAndRequestPreDepositTrue(InstallmentApplication.Status status);

	Optional<InstallmentApplication> findByBankLoanId(String bankLoanId);
	Optional<InstallmentApplication> findFirstByDepositId(Long depositId);
	Optional<InstallmentApplication> findFirstByPreDepositId(Long preDepositId);
	@Query("select ia from InstallmentApplication ia left join fetch ia.vehicle where ia.id = :id")
	Optional<InstallmentApplication> findByIdWithVehicle(@Param("id") Long id);
	@Query("""
			select distinct ia
			from InstallmentApplication ia
			left join fetch ia.vehicle
			left join fetch ia.documents
			where ia.id = :id
			""")
	Optional<InstallmentApplication> findByIdWithVehicleAndDocuments(@Param("id") Long id);

	List<InstallmentApplication> findTop100ByStatusAndBankLoanIdIsNotNullOrderByUpdatedAtAsc(
			InstallmentApplication.Status status);
}
