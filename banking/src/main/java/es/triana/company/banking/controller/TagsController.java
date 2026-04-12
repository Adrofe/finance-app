package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;

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
import es.triana.company.banking.security.TenantContext;

@RestController
@RequestMapping("/v1/api/tags")
public class TagsController {

    private final TagService tagService;
    private final TenantContext tenantContext;

    public TagsController(TagService tagService, TenantContext tenantContext) {
        this.tagService = tagService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagDTO>>> getTags() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TagDTO> tags = tagService.getTagsByTenant(tenantId);
        ApiResponse<List<TagDTO>> response = new ApiResponse<>(200, "Tags retrieved successfully", tags);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TagDTO>> createTag(@Valid @RequestBody TagDTO tagDTO) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TagDTO createdTag = tagService.createTag(tagDTO, tenantId);
        ApiResponse<TagDTO> response = new ApiResponse<>(201, "Tag created successfully", createdTag);
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable("tagId") Long tagId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        tagService.deleteTag(tagId, tenantId);
        ApiResponse<Void> response = new ApiResponse<>(204, "Tag deleted successfully", null);
        return ResponseEntity.status(204).body(response);
    }
}