package com.bentork.ev_system.repository;

import com.bentork.ev_system.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailOrMobile(String email, String mobile);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

}


