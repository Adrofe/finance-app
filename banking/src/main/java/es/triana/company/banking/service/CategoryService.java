package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.CategoryDTO;
import es.triana.company.banking.repository.CategoryRepository;
import es.triana.company.banking.service.mapper.CategoryMapper;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryMapper categoryMapper;

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    public List<CategoryDTO> getRootCategories() {
        return categoryRepository.findAllByParentIsNullOrderByNameAsc()
                .stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    public List<CategoryDTO> getSubCategories(Long parentId) {
        return categoryRepository.findAllByParentIdOrderByNameAsc(parentId)
                .stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    public CategoryDTO getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(categoryMapper::toDto)
                .orElse(null);
    }

    public CategoryDTO createCategory(CategoryDTO categoryDTO) {
        var category = categoryMapper.toEntity(categoryDTO);
        var saved = categoryRepository.save(category);
        return categoryMapper.toDto(saved);
    }

    public CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO) {
        if (!categoryRepository.existsById(id)) {
            return null;
        }
        categoryDTO.setId(id);
        var category = categoryMapper.toEntity(categoryDTO);
        var saved = categoryRepository.save(category);
        return categoryMapper.toDto(saved);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
