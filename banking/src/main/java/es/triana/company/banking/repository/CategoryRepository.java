package es.triana.company.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    List<Category> findAllByParentIsNullOrderByNameAsc();

    List<Category> findAllByParentIdOrderByNameAsc(Long parentId);
}
