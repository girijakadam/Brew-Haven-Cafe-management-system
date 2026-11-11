package com.brewhaven.controller;

import com.brewhaven.entity.CafeTable;
import com.brewhaven.service.TableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public ResponseEntity<List<CafeTable>> getAllTables() {
        return ResponseEntity.ok(tableService.getAllTables());
    }

    @GetMapping("/{tableNumber}")
    public ResponseEntity<CafeTable> getTable(@PathVariable Integer tableNumber) {
        return ResponseEntity.ok(tableService.getTableByNumber(tableNumber));
    }

    @PatchMapping("/{tableNumber}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<CafeTable> updateStatus(@PathVariable Integer tableNumber,
                                                  @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "AVAILABLE");
        CafeTable updated = tableService.updateTableStatus(tableNumber, status);
        return ResponseEntity.ok(updated);
    }
}
