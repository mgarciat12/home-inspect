package com.homeinspect.app.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CommentTemplateRepository extends JpaRepository<CommentTemplate, Long> {

    List<CommentTemplate> findByItemOrderByPositionAscIdAsc(ItemDef item);

    @Query("select c from CommentTemplate c join fetch c.item where c.item.id in :itemIds order by c.position, c.id")
    List<CommentTemplate> findByItemIds(Collection<Long> itemIds);

    long countByItem(ItemDef item);
}
