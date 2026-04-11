package es.triana.company.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByTenantIdOrderByNameAsc(Long tenantId);

    Optional<Tag> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Tag> findByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
}