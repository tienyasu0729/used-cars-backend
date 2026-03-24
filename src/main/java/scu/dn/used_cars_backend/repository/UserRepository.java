package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

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
}
