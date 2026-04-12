package es.triana.company.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TagDTO;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.TagService;
import es.triana.company.banking.service.exception.TagNotFoundException;

@SpringBootTest
@ActiveProfiles("test")
public class TagsControllerTest {

    @InjectMocks
    private TagsController tagsController;

    @Mock
    private TagService tagService;

    @Mock
    private TenantContext tenantContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tenantContext.getCurrentTenantId()).thenReturn(1L);
    }

    @Test
    public void getTags() {
        List<TagDTO> tags = List.of(TagDTO.builder().id(7L).name("vacation").build());

        when(tagService.getTagsByTenant(1L)).thenReturn(tags);

        ResponseEntity<ApiResponse<List<TagDTO>>> response = tagsController.getTags();

        verify(tagService).getTagsByTenant(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("vacation", response.getBody().getData().get(0).getName());
    }

    @Test
    public void createTag() {
        TagDTO tagDTO = TagDTO.builder().name("work").build();
        TagDTO createdTag = TagDTO.builder().id(8L).name("work").build();

        when(tagService.createTag(tagDTO, 1L)).thenReturn(createdTag);

        ResponseEntity<ApiResponse<TagDTO>> response = tagsController.createTag(tagDTO);

        verify(tagService).createTag(tagDTO, 1L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(8L, response.getBody().getData().getId());
    }

    @Test
    public void deleteTag() {
        ResponseEntity<ApiResponse<Void>> response = tagsController.deleteTag(7L);

        verify(tagService).deleteTag(7L, 1L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    public void deleteTagNotFound() {
        Long tagId = 999L;
        when(tenantContext.getCurrentTenantId()).thenReturn(1L);
        doThrow(new TagNotFoundException("Tag not found with id: " + tagId)).when(tagService).deleteTag(tagId, 1L);

        TagNotFoundException exception = assertThrows(TagNotFoundException.class,
                () -> tagsController.deleteTag(tagId));

        verify(tagService).deleteTag(tagId, 1L);
        assertEquals("Tag not found with id: " + tagId, exception.getMessage());
    }
}