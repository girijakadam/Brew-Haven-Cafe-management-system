package com.brewhaven.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "cafe_tables")
public class CafeTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, unique = true)
    private Integer tableNumber;

    private Integer capacity = 4;

    private String status = "AVAILABLE"; // AVAILABLE, OCCUPIED

    public CafeTable() {
    }

    public CafeTable(Integer tableNumber, Integer capacity) {
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.status = "AVAILABLE";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
