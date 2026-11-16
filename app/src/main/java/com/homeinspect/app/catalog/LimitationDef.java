package com.homeinspect.app.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Limitation offered on every item (objects stored, no access...). */
@Entity
@Table(name = "limitation_def")
@Getter
@Setter
@NoArgsConstructor
public class LimitationDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String label;

    @Column(nullable = false)
    private int position;

    public LimitationDef(String label, int position) {
        this.label = label;
        this.position = position;
    }
}
