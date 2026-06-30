package com.ybank.core.transfer;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
interface TransferRepository extends JpaRepository<Transfer,Long>{ List<Transfer> findTop20ByOrderByCreatedAtDesc(); }
