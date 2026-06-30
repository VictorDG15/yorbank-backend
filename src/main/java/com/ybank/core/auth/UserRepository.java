package com.ybank.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
  Optional<User> findByDocumentNumber(String documentNumber);
  boolean existsByEmail(String email);
  boolean existsByDocumentNumber(String documentNumber);
}
