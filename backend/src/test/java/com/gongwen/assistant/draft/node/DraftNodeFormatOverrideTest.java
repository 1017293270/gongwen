package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftNodeFormatOverrideTest {
    @Test
    void savesFormatOverrideAfterDraftAccessCheck() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        long nodeId = nodes.add(nodeWithOverride(DraftNodeFormatOverride.empty(), "USER_FILLED")).id();
        DraftNodeService service = service(draftService, nodes);

        DraftNodeDto updated = service.saveFormatOverride(5L, nodeId, new DraftNodeFormatOverride(
                " KaiTi ",
                " Times New Roman ",
                16.0,
                true,
                " center ",
                560,
                " exact ",
                590,
                120,
                240
        ));

        verify(draftService).getDraft(5L);
        assertThat(updated.status()).isEqualTo("FORMAT_OVERRIDDEN");
        assertThat(updated.formatOverride()).isEqualTo(new DraftNodeFormatOverride(
                "KaiTi",
                "Times New Roman",
                16.0,
                true,
                "CENTER",
                560,
                "EXACT",
                590,
                120,
                240
        ));
    }

    @Test
    void restoresTemplateDefaultByClearingDraftOverride() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        long nodeId = nodes.add(nodeWithOverride(new DraftNodeFormatOverride(
                "KaiTi",
                "Times New Roman",
                16.0,
                true,
                "CENTER",
                560,
                "EXACT",
                590,
                120,
                240
        ), "FORMAT_OVERRIDDEN")).id();
        DraftNodeService service = service(draftService, nodes);

        DraftNodeDto restored = service.restoreTemplateDefaultFormatting(5L, nodeId);

        verify(draftService).getDraft(5L);
        assertThat(restored.formatOverride()).isEqualTo(DraftNodeFormatOverride.empty());
        assertThat(restored.status()).isEqualTo("USER_FILLED");
    }

    private DraftNodeService service(DraftService draftService, InMemoryDraftNodeRepository nodes) {
        return new DraftNodeService(
                draftService,
                nodes,
                new FixedMappingRepository(),
                new FixedStructureProfileRepository(),
                new DraftNodeFormattingResolver(
                        new FixedStructureProfileRepository(),
                        new FixedFormattingRepository(),
                        new TemplateEffectiveFormattingService()
                )
        );
    }

    private DraftDetailDto draftWithTemplate() {
        return new DraftDetailDto(5L, "NOTICE", "测试通知", "DRAFT", 9L, List.of(
                new DraftBlockDto(1L, "TITLE", "测试通知", 10)
        ));
    }

    private DraftNode nodeWithOverride(DraftNodeFormatOverride override, String status) {
        return new DraftNode(
                0,
                5L,
                21L,
                "body-node",
                null,
                "PARAGRAPH",
                "BODY",
                "body",
                "正文",
                "正文内容",
                30,
                status,
                override,
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
    }

    private record FixedStructureProfileRepository() implements DocumentStructureProfileRepository {
        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.empty();
        }
    }

    private record FixedMappingRepository() implements StructureMappingRepository {
        @Override
        public StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser) {
            throw new UnsupportedOperationException("save is not used in this test");
        }

        @Override
        public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
            return Optional.empty();
        }

        @Override
        public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
            return Optional.empty();
        }

        @Override
        public int nextVersionNo(long templateVersionId) {
            return 1;
        }
    }

    private record FixedFormattingRepository() implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return Map.of();
        }

        @Override
        public void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting) {
            throw new UnsupportedOperationException("saveOverride is not used in this test");
        }
    }

    private static final class InMemoryDraftNodeRepository implements DraftNodeRepository {
        private final Map<Long, List<DraftNode>> byDraft = new HashMap<>();
        private long nextId = 100L;

        DraftNode add(DraftNode node) {
            DraftNode saved = new DraftNode(
                    nextId++,
                    node.draftId(),
                    node.structureMappingProfileId(),
                    node.templateNodeKey(),
                    node.parentTemplateNodeKey(),
                    node.nodeType(),
                    node.role(),
                    node.slotKey(),
                    node.title(),
                    node.content(),
                    node.sortOrder(),
                    node.status(),
                    node.formatOverride(),
                    node.createdAt(),
                    node.updatedAt()
            );
            byDraft.computeIfAbsent(saved.draftId(), ignored -> new ArrayList<>()).add(saved);
            return saved;
        }

        @Override
        public List<DraftNode> findByDraftId(long draftId) {
            return byDraft.getOrDefault(draftId, List.of()).stream()
                    .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                    .toList();
        }

        @Override
        public boolean existsByDraftId(long draftId) {
            return !findByDraftId(draftId).isEmpty();
        }

        @Override
        public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
            byDraft.put(draftId, nodes.stream().map(this::add).toList());
            return findByDraftId(draftId);
        }

        @Override
        public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
            return update(draftId, nodeId, node -> new DraftNode(
                    node.id(),
                    node.draftId(),
                    node.structureMappingProfileId(),
                    node.templateNodeKey(),
                    node.parentTemplateNodeKey(),
                    node.nodeType(),
                    node.role(),
                    node.slotKey(),
                    node.title(),
                    content,
                    node.sortOrder(),
                    status,
                    node.formatOverride(),
                    node.createdAt(),
                    Instant.now()
            ));
        }

        @Override
        public Optional<DraftNode> updateFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride override, String status) {
            return update(draftId, nodeId, node -> new DraftNode(
                    node.id(),
                    node.draftId(),
                    node.structureMappingProfileId(),
                    node.templateNodeKey(),
                    node.parentTemplateNodeKey(),
                    node.nodeType(),
                    node.role(),
                    node.slotKey(),
                    node.title(),
                    node.content(),
                    node.sortOrder(),
                    status,
                    override,
                    node.createdAt(),
                    Instant.now()
            ));
        }

        private Optional<DraftNode> update(long draftId, long nodeId, java.util.function.Function<DraftNode, DraftNode> updater) {
            List<DraftNode> nodes = new ArrayList<>(findByDraftId(draftId));
            for (int index = 0; index < nodes.size(); index++) {
                DraftNode node = nodes.get(index);
                if (node.id() == nodeId) {
                    DraftNode updated = updater.apply(node);
                    nodes.set(index, updated);
                    byDraft.put(draftId, nodes);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }
    }
}
