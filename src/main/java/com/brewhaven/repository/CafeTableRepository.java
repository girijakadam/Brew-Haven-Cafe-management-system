package com.brewhaven.repository;

import com.brewhaven.entity.CafeTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CafeTableRepository extends JpaRepository<CafeTable, Long> {
    Optional<CafeTable> findByTableNumber(Integer tableNumber);
}
