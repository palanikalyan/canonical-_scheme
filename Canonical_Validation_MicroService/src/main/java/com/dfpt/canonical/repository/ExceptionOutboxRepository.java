package com.dfpt.canonical.repository;

import com.dfpt.canonical.model.ExceptionOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExceptionOutboxRepository extends JpaRepository<ExceptionOutboxEntity, UUID> {

}
