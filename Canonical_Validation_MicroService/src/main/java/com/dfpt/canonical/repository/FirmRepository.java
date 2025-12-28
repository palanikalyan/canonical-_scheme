package com.dfpt.canonical.repository;

import com.dfpt.canonical.model.Firm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FirmRepository extends JpaRepository<Firm, Integer> {
    Optional<Firm> findByFirmNumber(Integer firmNumber);
}
