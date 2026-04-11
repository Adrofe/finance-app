package es.triana.company.banking.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TagDTO;
import es.triana.company.banking.model.db.Tag;
import es.triana.company.banking.repository.TagRepository;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<TagDTO> getTagsByTenant(Long tenantId) {
        validateTenantId(tenantId);

        return tagRepository.findAllByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TagDTO createTag(TagDTO tagDTO, Long tenantId) {
        if (tagDTO == null) {
            throw new IllegalArgumentException("Tag payload is required");
        }

        validateTenantId(tenantId);

        String normalizedName = normalizeName(tagDTO.getName());
        if (tagRepository.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)) {
            throw new IllegalArgumentException("Tag with name '" + normalizedName + "' already exists for tenant " + tenantId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Tag savedTag = tagRepository.save(Tag.builder()
                .tenantId(tenantId)
                .name(normalizedName)
                .createdAt(now)
                .updatedAt(now)
                .build());

        return toDto(savedTag);
    }

    public void deleteTag(Long tagId, Long tenantId) {
        validateTenantId(tenantId);
        if (tagId == null) {
            throw new IllegalArgumentException("Tag id is required");
        }

        Tag tag = tagRepository.findByIdAndTenantId(tagId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found with id: " + tagId));

        tagRepository.delete(tag);
    }

    private TagDTO toDto(Tag tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .tenantId(tag.getTenantId())
                .name(tag.getName())
                .build();
    }

    private void validateTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Tag name is required");
        }

        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Tag name is required");
        }

        if (normalizedName.length() > 64) {
            throw new IllegalArgumentException("Tag name must be at most 64 characters");
        }

        return normalizedName;
    }
}