package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.CategoryDTO;
import es.triana.company.banking.model.db.Category;

@Component
public class CategoryMapper {

    public CategoryDTO toDto(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .code(category.getCode())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .isFixed(category.getIsFixed())
                .build();
    }

    public Category toEntity(CategoryDTO categoryDTO) {
        if (categoryDTO == null) {
            return null;
        }
        return Category.builder()
                .id(categoryDTO.getId())
                .name(categoryDTO.getName())
                .code(categoryDTO.getCode())
                .isFixed(categoryDTO.getIsFixed())
                .build();
    }
}
