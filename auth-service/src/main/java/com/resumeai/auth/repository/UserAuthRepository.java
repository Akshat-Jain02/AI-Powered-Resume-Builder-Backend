package com.resumeai.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.resumeai.auth.entity.UserAuthEntity;

public interface UserAuthRepository extends JpaRepository<UserAuthEntity, Integer>{

	Optional<UserAuthEntity> findByUsername(String username);
	Optional<UserAuthEntity> findByEmail(String email);
	boolean existsByUsername(String username);
	boolean existsByEmail(String email);

}
