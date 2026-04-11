package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TagDTO;
import es.triana.company.banking.service.TagService;

@RestController
@RequestMapping("/v1/api/tags")
public class TagsController {

    private static final Long DEFAULT_TENANT_ID = 1L;

    private final TagService tagService;

    public TagsController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagDTO>>> getTags() {
        try {
            List<TagDTO> tags = tagService.getTagsByTenant(DEFAULT_TENANT_ID);
            ApiResponse<List<TagDTO>> response = new ApiResponse<>(200, "Tags retrieved successfully", tags);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<TagDTO>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TagDTO>> createTag(@Valid @RequestBody TagDTO tagDTO) {
        try {
            Long tenantId = resolveTenantId(tagDTO.getTenantId());
            TagDTO createdTag = tagService.createTag(tagDTO, tenantId);
            ApiResponse<TagDTO> response = new ApiResponse<>(201, "Tag created successfully", createdTag);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<TagDTO> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long tagId) {
        try {
            tagService.deleteTag(tagId, DEFAULT_TENANT_ID);
            ApiResponse<Void> response = new ApiResponse<>(204, "Tag deleted successfully", null);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private Long resolveTenantId(Long tenantId) {
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }
}