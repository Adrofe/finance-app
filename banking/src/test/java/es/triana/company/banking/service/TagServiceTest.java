package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import es.triana.company.banking.model.api.TagDTO;
import es.triana.company.banking.model.db.Tag;
import es.triana.company.banking.repository.TagRepository;
import es.triana.company.banking.service.exception.TagConflictException;
import es.triana.company.banking.service.exception.TagNotFoundException;
import es.triana.company.banking.service.exception.TagValidationException;

class TagServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long TAG_ID = 7L;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Tag buildTag(Long id, Long tenantId, String name) {
        return Tag.builder()
                .id(id)
                .tenantId(tenantId)
                .name(name)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getTagsByTenant")
    class GetTagsByTenant {

        @Test
        @DisplayName("should return tags sorted by repository order")
        void getTagsSuccess() {
            when(tagRepository.findAllByTenantIdOrderByNameAsc(TENANT_ID))
                    .thenReturn(List.of(
                            buildTag(1L, TENANT_ID, "food"),
                            buildTag(2L, TENANT_ID, "travel")));

            List<TagDTO> result = tagService.getTagsByTenant(TENANT_ID);

            assertEquals(2, result.size());
            assertEquals("food", result.get(0).getName());
            assertEquals("travel", result.get(1).getName());
        }

        @Test
        @DisplayName("should return empty list when tenant has no tags")
        void getTagsEmpty() {
            when(tagRepository.findAllByTenantIdOrderByNameAsc(TENANT_ID)).thenReturn(Collections.emptyList());

            List<TagDTO> result = tagService.getTagsByTenant(TENANT_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject null tenant id")
        void rejectNullTenantId() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.getTagsByTenant(null));

            assertEquals("Tenant id is required", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("createTag")
    class CreateTag {

        @Test
        @DisplayName("should create tag with normalized name")
        void createTagSuccess() {
            TagDTO input = TagDTO.builder().name("  work  ").build();
            Tag saved = buildTag(TAG_ID, TENANT_ID, "work");

            when(tagRepository.existsByTenantIdAndNameIgnoreCase(TENANT_ID, "work")).thenReturn(false);
            when(tagRepository.save(org.mockito.ArgumentMatchers.any(Tag.class))).thenReturn(saved);

            TagDTO result = tagService.createTag(input, TENANT_ID);

            assertNotNull(result);
            assertEquals(TAG_ID, result.getId());
            assertEquals("work", result.getName());

            ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
            verify(tagRepository).save(captor.capture());
            assertEquals(TENANT_ID, captor.getValue().getTenantId());
            assertEquals("work", captor.getValue().getName());
            assertNotNull(captor.getValue().getCreatedAt());
            assertNotNull(captor.getValue().getUpdatedAt());
        }

        @Test
        @DisplayName("should reject null payload")
        void rejectNullPayload() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.createTag(null, TENANT_ID));

            assertEquals("Tag payload is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject null tenant id")
        void rejectNullTenantId() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.createTag(TagDTO.builder().name("work").build(), null));

            assertEquals("Tenant id is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject null name")
        void rejectNullName() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.createTag(TagDTO.builder().name(null).build(), TENANT_ID));

            assertEquals("Tag name is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject blank name")
        void rejectBlankName() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.createTag(TagDTO.builder().name("   ").build(), TENANT_ID));

            assertEquals("Tag name is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject name longer than 64 chars")
        void rejectTooLongName() {
            String longName = "a".repeat(65);

            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.createTag(TagDTO.builder().name(longName).build(), TENANT_ID));

            assertEquals("Tag name must be at most 64 characters", exception.getMessage());
        }

        @Test
        @DisplayName("should reject duplicate tag name for tenant")
        void rejectDuplicateName() {
            TagDTO input = TagDTO.builder().name("work").build();
            when(tagRepository.existsByTenantIdAndNameIgnoreCase(TENANT_ID, "work")).thenReturn(true);

            TagConflictException exception = assertThrows(TagConflictException.class,
                    () -> tagService.createTag(input, TENANT_ID));

            assertEquals("Tag with name 'work' already exists for tenant 1", exception.getMessage());
            verify(tagRepository, never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
        }
    }

    @Nested
    @DisplayName("deleteTag")
    class DeleteTag {

        @Test
        @DisplayName("should delete tag for tenant")
        void deleteTagSuccess() {
            Tag tag = buildTag(TAG_ID, TENANT_ID, "work");
            when(tagRepository.findByIdAndTenantId(TAG_ID, TENANT_ID)).thenReturn(Optional.of(tag));

            tagService.deleteTag(TAG_ID, TENANT_ID);

            verify(tagRepository, times(1)).delete(tag);
        }

        @Test
        @DisplayName("should reject null tenant id")
        void rejectNullTenantId() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.deleteTag(TAG_ID, null));

            assertEquals("Tenant id is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject null tag id")
        void rejectNullTagId() {
            TagValidationException exception = assertThrows(TagValidationException.class,
                    () -> tagService.deleteTag(null, TENANT_ID));

            assertEquals("Tag id is required", exception.getMessage());
        }

        @Test
        @DisplayName("should reject non-existent tag")
        void rejectTagNotFound() {
            when(tagRepository.findByIdAndTenantId(TAG_ID, TENANT_ID)).thenReturn(Optional.empty());

            TagNotFoundException exception = assertThrows(TagNotFoundException.class,
                    () -> tagService.deleteTag(TAG_ID, TENANT_ID));

            assertEquals("Tag not found with id: 7", exception.getMessage());
        }
    }
}