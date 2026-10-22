package com.tcet.RestApi.repositories;

import com.tcet.RestApi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
