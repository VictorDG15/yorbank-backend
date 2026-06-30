package com.ybank.core.account;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
interface AccountRepository extends JpaRepository<Account,Long>{ List<Account> findByCustomerId(Long customerId); Optional<Account> findByAccountNumber(String accountNumber); }
