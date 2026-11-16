package com.homeinspect.app.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    @Query("select p from Photo p where p.item.id in :itemIds order by p.position, p.id")
    List<Photo> findByItemIds(Collection<Long> itemIds);

    @Query("select p from Photo p where p.item.id = :itemId order by p.position, p.id")
    List<Photo> findByItemId(long itemId);

    @Query("""
            select p from Photo p
            join fetch p.item i join fetch i.section s join fetch s.report r join fetch r.inspector
            where p.id = :photoId and r.id = :reportId
            """)
    Optional<Photo> findInReport(long reportId, long photoId);

    @Query("select coalesce(max(p.position), 0) from Photo p where p.item.id = :itemId")
    int maxPosition(long itemId);

    @Query("select p from Photo p where p.item.section.report.id = :reportId order by p.id")
    List<Photo> findByReportId(long reportId);
}
