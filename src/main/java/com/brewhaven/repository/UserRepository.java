package com.brewhaven.repository;

import com.brewhaven.entity.Role;
import com.brewhaven.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);

    List<User> findByRole(Role role);
    long countByRole(Role role);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:identifier) OR LOWER(u.email) = LOWER(:identifier) OR u.phone = :identifier OR LOWER(u.name) = LOWER(:identifier)")
    List<User> findByIdentifier(@Param("identifier") String identifier);
}
