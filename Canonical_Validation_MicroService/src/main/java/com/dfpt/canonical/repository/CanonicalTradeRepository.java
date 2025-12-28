package com.dfpt.canonical.repository;

import com.dfpt.canonical.model.CanonicalTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CanonicalTradeRepository extends JpaRepository<CanonicalTrade, UUID> {
     boolean existsByTransactionIdAndFileId(String transactionId, UUID fileId);
    
    boolean existsByRawOrderId(UUID rawOrderId);
}
