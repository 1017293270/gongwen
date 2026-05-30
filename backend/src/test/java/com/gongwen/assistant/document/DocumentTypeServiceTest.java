package com.gongwen.assistant.document;

import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTypeServiceTest {
    private final DocumentTypeRepository repository = mock(DocumentTypeRepository.class);
    private final DocumentTypeService service = new DocumentTypeService(repository);

    @Test
    void createsDocumentTypeWithNormalizedCodeAndTrimmedName() {
        when(repository.create("MEETING_NOTE", "会议纪要", 4))
                .thenReturn(new DocumentTypeDto("MEETING_NOTE", "会议纪要", "ACTIVE", 4));

        DocumentTypeDto result = service.create(new CreateDocumentTypeRequest(" meeting_note ", " 会议纪要 ", 4));

        assertThat(result.code()).isEqualTo("MEETING_NOTE");
        assertThat(result.name()).isEqualTo("会议纪要");
    }

    @Test
    void rejectsInvalidDocumentTypeCode() {
        assertThatThrownBy(() -> service.create(new CreateDocumentTypeRequest("会议", "会议纪要", 4)))
                .isInstanceOf(DocumentTypeException.class)
                .hasMessage("Document type code can only contain uppercase letters, numbers, and underscores");
    }

    @Test
    void updatesDocumentTypeNameAndSortOrder() {
        when(repository.update("NOTICE", "通知公文", 8))
                .thenReturn(Optional.of(new DocumentTypeDto("NOTICE", "通知公文", "ACTIVE", 8)));

        DocumentTypeDto result = service.update("notice", new UpdateDocumentTypeRequest(" 通知公文 ", 8));

        assertThat(result).isEqualTo(new DocumentTypeDto("NOTICE", "通知公文", "ACTIVE", 8));
    }

    @Test
    void blocksDeletingDocumentTypeWithDrafts() {
        when(repository.countDrafts("NOTICE")).thenReturn(2);

        assertThatThrownBy(() -> service.delete("NOTICE"))
                .isInstanceOf(DocumentTypeException.class)
                .hasMessage("Document type still has drafts and cannot be deleted");
    }

    @Test
    void blocksDeletingDocumentTypeWithTemplates() {
        when(repository.countDrafts("NOTICE")).thenReturn(0);
        when(repository.countTemplates("NOTICE")).thenReturn(1);

        assertThatThrownBy(() -> service.delete("NOTICE"))
                .isInstanceOf(DocumentTypeException.class)
                .hasMessage("Document type still has templates and cannot be deleted");
    }

    @Test
    void deletesUnusedDocumentType() {
        when(repository.countDrafts("MEETING")).thenReturn(0);
        when(repository.countTemplates("MEETING")).thenReturn(0);
        when(repository.delete("MEETING")).thenReturn(true);

        service.delete("meeting");

        verify(repository).delete("MEETING");
    }

    @Test
    void createsAndListsDocumentTypesForCurrentUserWhenAuthenticationIsAvailable() {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        CurrentUser currentUser = new CurrentUser(
                7L,
                "template-admin",
                "Template Admin",
                3L,
                "General Office",
                List.of("TEMPLATE_ADMIN"));
        when(currentUserProvider.currentUser()).thenReturn(currentUser);
        DocumentTypeService authenticatedService = new DocumentTypeService(repository, currentUserProvider);
        when(repository.create("MEETING", "会议纪要", 4, currentUser))
                .thenReturn(new DocumentTypeDto("MEETING", "会议纪要", "ACTIVE", 4));
        when(repository.findVisible(currentUser)).thenReturn(List.of(new DocumentTypeDto("MEETING", "会议纪要", "ACTIVE", 4)));

        authenticatedService.create(new CreateDocumentTypeRequest("meeting", "会议纪要", 4));
        authenticatedService.findActive();

        verify(repository).create("MEETING", "会议纪要", 4, currentUser);
        verify(repository).findVisible(currentUser);
    }
}
