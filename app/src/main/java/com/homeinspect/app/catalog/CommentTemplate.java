package com.homeinspect.app.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Predefined phrase of the comment library, inserted with one click in the observations. */
@Entity
@Table(name = "comment_template")
@Getter
@Setter
@NoArgsConstructor
public class CommentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", updatable = false)
    private ItemDef item;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private int position;

    public CommentTemplate(ItemDef item, String text, int position) {
        this.item = item;
        this.text = text;
        this.position = position;
    }
}
