package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

	@Query("""
			select distinct u from User u
			left join fetch u.userRoles ur
			left join fetch ur.role
			where lower(u.email) = lower(:email) and u.deleted = false
			""")
	Optional<User> findActiveByEmailWithRoles(@Param("email") String email);

	boolean existsByIdAndDeletedFalse(Long id);

	Optional<User> findByIdAndDeletedFalse(Long id);

	@EntityGraph(attributePaths = { "userRoles", "userRoles.role" })
	@Query("select u from User u where u.id = :id and u.deleted = false")
	Optional<User> findActiveByIdWithRoles(@Param("id") Long id);

	@EntityGraph(attributePaths = { "userRoles", "userRoles.role" })
	@Query("""
			select u from User u
			where u.id in (
				select sa.userId from StaffAssignment sa
				where sa.branchId = :branchId and sa.active = true
					and (sa.endDate is null or sa.endDate >= CURRENT_DATE)
			)
			and u.deleted = false and lower(u.status) = 'active'
			order by u.name asc
			""")
	List<User> findActiveStaffUsersByBranchId(@Param("branchId") int branchId);

	@EntityGraph(attributePaths = { "userRoles", "userRoles.role" })
	@Query("""
			select distinct u from User u
			join u.userRoles ur
			join ur.role r
			where r.name in ('SalesStaff', 'BranchManager')
			and (
				(u.deleted = false and (
					:branchId is null
					or u.id in (
						select sa.userId from StaffAssignment sa
						where sa.active = true and sa.branchId = :branchId
					)
					or u.id in (
						select b.manager.id from Branch b
						where b.deleted = false and b.manager is not null and b.id = :branchId
					)
				))
				or (u.deleted = true and :branchId is not null and (
					u.id in (
						select sa2.userId from StaffAssignment sa2
						where sa2.branchId = :branchId
					)
					or u.id in (
						select b2.manager.id from Branch b2
						where b2.deleted = false and b2.manager is not null and b2.id = :branchId
					)
				))
			)
			order by u.deleted asc, u.name asc
			""")
	List<User> findStaffUsersForManagerList(@Param("branchId") Integer branchId);

	@EntityGraph(attributePaths = { "userRoles", "userRoles.role" })
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdWithRoles(@Param("id") Long id);

	boolean existsByPhoneIgnoreCaseAndDeletedFalse(String phone);

	boolean existsByPhoneIgnoreCaseAndDeletedFalseAndIdNot(String phone, Long id);
}
