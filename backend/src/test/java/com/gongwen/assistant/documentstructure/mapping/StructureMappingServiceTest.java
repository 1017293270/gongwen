package com.gongwen.assistant.documentstructure.mapping;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.template.profile.TemplateAnalysisProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructureMappingServiceTest {
    @Test
    void savesDraftAfterValidatingNodeKeys() {
        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService service = service(mappings, styleTemplateProfile());

        StructureMappingProfile saved = service.saveDraft(9L, new SaveStructureMappingRequest(null, List.of(
                item("title-node", "TITLE", "CONFIRMED"),
                item("body-node", "BODY", "CONFIRMED")
        )));

        assertThat(saved.mappingProfileId()).isEqualTo(1L);
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.versionNo()).isEqualTo(1);
        assertThat(saved.confirmedCount()).isEqualTo(2);
        assertThat(saved.items()).extracting(StructureMappingItem::slotKey).contains("title", "body");
    }

    @Test
    void rejectsDraftWithUnknownNodeKey() {
        StructureMappingService service = service(new InMemoryStructureMappingRepository(), styleTemplateProfile());

        assertThatThrownBy(() -> service.saveDraft(9L, new SaveStructureMappingRequest(null, List.of(
                        item("missing-node", "TITLE", "CONFIRMED")
                ))))
                .isInstanceOfSatisfying(StructureMappingException.class, exception ->
                        assertThat(((StructureMappingException) exception).errorCode()).isEqualTo("STRUCTURE_MAPPING_NODE_NOT_FOUND"));
    }

    @Test
    void publishBlocksManualDocumentWithoutAdminOverride() {
        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService service = service(mappings, manualProfile());
        service.saveDraft(9L, new SaveStructureMappingRequest(null, List.of(
                item("title-node", "TITLE", "CONFIRMED"),
                item("body-node", "BODY", "CONFIRMED")
        )));

        StructureMappingProfile result = service.publish(9L, new PublishStructureMappingRequest(false));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.validationItems()).extracting(StructureMappingValidationItem::code)
                .contains("DOCUMENT_KIND_BLOCKED");
    }

    @Test
    void publishBlocksMissingRequiredSlots() {
        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService service = service(mappings, styleTemplateProfile());
        service.saveDraft(9L, new SaveStructureMappingRequest(null, List.of(
                item("title-node", "TITLE", "CONFIRMED")
        )));

        StructureMappingProfile result = service.publish(9L, new PublishStructureMappingRequest(false));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.validationItems()).extracting(StructureMappingValidationItem::code)
                .contains("REQUIRED_SLOT_MISSING");
        assertThat(result.validationItems()).extracting(StructureMappingValidationItem::role)
                .contains("BODY");
    }

    @Test
    void publishesConfirmedTitleAndBody() {
        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService service = service(mappings, styleTemplateProfile());
        service.saveDraft(9L, new SaveStructureMappingRequest(null, List.of(
                item("title-node", "TITLE", "CONFIRMED"),
                item("body-node", "BODY", "CONFIRMED")
        )));

        StructureMappingProfile result = service.publish(9L, new PublishStructureMappingRequest(false));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.validationItems()).isEmpty();
        assertThat(result.publishedAt()).isNotNull();
    }

    private StructureMappingService service(
            InMemoryStructureMappingRepository mappingRepository,
            TemplateProfile templateProfile
    ) {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUser()).thenReturn(new CurrentUser(
                1L,
                "template-admin",
                "Template Admin",
                2L,
                "Office",
                List.of("TEMPLATE_ADMIN")
        ));
        return new StructureMappingService(
                mappingRepository,
                new FixedDocumentStructureProfileRepository(structureProfile()),
                new FixedTemplateProfileRepository(templateProfile),
                currentUserProvider
        );
    }

    private StructureMappingItem item(String nodeKey, String role, String status) {
        return new StructureMappingItem(nodeKey, role, "", status, "USER", 1.0, "", role.hashCode());
    }

    private DocumentStructureProfile structureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash-9",
                "document-structure-v1",
                List.of(
                        node("title-node", "TITLE", 10),
                        node("body-node", "BODY", 20),
                        node("date-node", "DATE", 30)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentNode node(String nodeKey, String role, int orderIndex) {
        return new DocumentNode(
                nodeKey,
                null,
                "PARAGRAPH",
                role,
                role + " text",
                role + " text",
                orderIndex,
                "PARAGRAPH/" + orderIndex,
                null,
                List.of()
        );
    }

    private TemplateProfile styleTemplateProfile() {
        return emptyProfile().withTemplateAnalysis(new TemplateAnalysisProfile(
                "STYLE_TEMPLATE",
                0.9,
                "NOTICE",
                List.of(),
                List.of(),
                "style template",
                "rules",
                "STYLE_TEMPLATE",
                List.of("OFFICIAL_STRUCTURE"),
                "REVIEW_AND_MAP",
                List.of()
        ));
    }

    private TemplateProfile manualProfile() {
        return emptyProfile().withTemplateAnalysis(new TemplateAnalysisProfile(
                "ORDINARY_DOCUMENT",
                0.9,
                "NOTICE",
                List.of(),
                List.of(),
                "manual",
                "rules",
                "MANUAL_OR_GUIDE",
                List.of("MANUAL_OR_GUIDE_KEYWORD"),
                "BLOCK_AUTO_TEMPLATE",
                List.of("manual blocked")
        ));
    }

    private TemplateProfile emptyProfile() {
        return new TemplateProfile(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private record FixedDocumentStructureProfileRepository(DocumentStructureProfile profile)
            implements DocumentStructureProfileRepository {
        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.of(profile);
        }
    }

    private record FixedTemplateProfileRepository(TemplateProfile profile) implements TemplateProfileRepository {
        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.of(profile);
        }
    }

    private static class InMemoryStructureMappingRepository implements StructureMappingRepository {
        private final Map<Long, StructureMappingProfile> profiles = new HashMap<>();
        private long nextId = 1L;

        @Override
        public StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser) {
            long id = nextId++;
            StructureMappingProfile saved = new StructureMappingProfile(
                    id,
                    profile.templateVersionId(),
                    profile.versionNo(),
                    profile.status(),
                    profile.items(),
                    profile.validationItems(),
                    0,
                    0,
                    profile.publishedAt(),
                    Instant.now(),
                    Instant.now()
            );
            profiles.put(id, saved);
            return saved;
        }

        @Override
        public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
            return profiles.values().stream()
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .max(Comparator.comparingInt(StructureMappingProfile::versionNo));
        }

        @Override
        public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
            return profiles.values().stream()
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .filter(profile -> status.equals(profile.status()))
                    .max(Comparator.comparingInt(StructureMappingProfile::versionNo));
        }

        @Override
        public int nextVersionNo(long templateVersionId) {
            return findLatest(templateVersionId)
                    .map(StructureMappingProfile::versionNo)
                    .orElse(0) + 1;
        }
    }
}
