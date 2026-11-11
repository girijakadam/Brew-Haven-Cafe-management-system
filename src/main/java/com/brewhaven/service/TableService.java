package com.brewhaven.service;

import com.brewhaven.entity.CafeTable;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.CafeTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TableService {

    private final CafeTableRepository cafeTableRepository;

    public TableService(CafeTableRepository cafeTableRepository) {
        this.cafeTableRepository = cafeTableRepository;
    }

    @Transactional(readOnly = true)
    public List<CafeTable> getAllTables() {
        return cafeTableRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CafeTable getTableByNumber(Integer tableNumber) {
        return cafeTableRepository.findByTableNumber(tableNumber)
                .orElseThrow(() -> new ResourceNotFoundException("CafeTable", "tableNumber", tableNumber));
    }

    @Transactional
    public CafeTable updateTableStatus(Integer tableNumber, String status) {
        CafeTable table = getTableByNumber(tableNumber);
        table.setStatus(status.toUpperCase());
        return cafeTableRepository.save(table);
    }
}
