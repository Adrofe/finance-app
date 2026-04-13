package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.CategoryDTO;
import es.triana.company.banking.service.CategoryService;

@RestController
@RequestMapping("/v1/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAllCategories() {
        List<CategoryDTO> categories = categoryService.getAllCategories();
        ApiResponse<List<CategoryDTO>> response = new ApiResponse<>(200, "Categories retrieved successfully", categories);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/root")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getRootCategories() {
        List<CategoryDTO> categories = categoryService.getRootCategories();
        ApiResponse<List<CategoryDTO>> response = new ApiResponse<>(200, "Root categories retrieved successfully", categories);
        return ResponseEntity.ok(response);
    }
}
