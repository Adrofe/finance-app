package es.triana.company.wealth.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.wealth.model.db.WealthSnapshot;

public interface WealthSnapshotRepository extends JpaRepository<WealthSnapshot, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<WealthSnapshot> findWithItemsByTenantIdAndSnapshotDate(Long tenantId, LocalDate snapshotDate);

    List<WealthSnapshot> findByTenantIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(Long tenantId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "items")
    List<WealthSnapshot> findWithItemsByTenantIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(Long tenantId, LocalDate from, LocalDate to);

    Optional<WealthSnapshot> findTopByTenantIdOrderBySnapshotDateDescSnapshotAtDesc(Long tenantId);

    @EntityGraph(attributePaths = "items")
    Optional<WealthSnapshot> findWithItemsTopByTenantIdOrderBySnapshotDateDescSnapshotAtDesc(Long tenantId);
}
