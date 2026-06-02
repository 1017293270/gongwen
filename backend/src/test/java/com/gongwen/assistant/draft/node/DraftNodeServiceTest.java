package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingItem;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.security.CurrentUser;
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

class DraftNodeServiceTest {
    @Test
    void initializesNodesFromPublishedMappingAndSourceText() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, nodes, publishedMapping(), structureProfile());

        List<DraftNodeDto> initialized = service.initializeNodes(5L);

        assertThat(initialized).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "BODY_HEADING_LEVEL_1", "BODY", "DATE");
        assertThat(initialized).extracting(DraftNodeDto::content)
                .containsExactly("模板标题", "一、工作安排", "模板正文", "2026年5月30日");
        assertThat(initialized).extracting(DraftNodeDto::status)
                .containsExactly("USER_FILLED", "USER_FILLED", "USER_FILLED", "USER_FILLED");
        assertThat(initialized).extracting(DraftNodeDto::structureMappingProfileId)
                .containsOnly(21L);
        assertThat(nodes.replaceCount).isEqualTo(1);
    }

    @Test
    void initializeReturnsExistingNodesWithoutDuplicating() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, nodes, publishedMapping(), structureProfile());

        service.initializeNodes(5L);
        List<DraftNodeDto> second = service.initializeNodes(5L);

        assertThat(second).hasSize(4);
        assertThat(nodes.replaceCount).isEqualTo(1);
    }

    @Test
    void initializeKeepsExistingNodesWhenPublishedMappingChangesUntilExplicitReinitialize() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        service(draftService, nodes, publishedMapping(), structureProfile()).initializeNodes(5L);

        List<DraftNodeDto> unchanged = service(draftService, nodes, publishedMappingV2(), structureProfile()).initializeNodes(5L);

        assertThat(unchanged).extracting(DraftNodeDto::structureMappingProfileId)
                .containsOnly(21L);
        assertThat(nodes.replaceCount).isEqualTo(1);

        List<DraftNodeDto> refreshed = service(draftService, nodes, publishedMappingV2(), structureProfile())
                .reinitializeNodes(5L, new ReinitializeDraftNodesRequest("FROM_SOURCE_DOCUMENT", false));

        assertThat(refreshed).extracting(DraftNodeDto::structureMappingProfileId)
                .containsOnly(22L);
        assertThat(nodes.replaceCount).isEqualTo(2);
    }

    @Test
    void initializesReferenceDocumentNodesFromSourceTextWhenDraftIsEmpty() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
        DraftNodeService service = service(draftService, new InMemoryDraftNodeRepository(), referencePublishedMapping(), referenceStructureProfile());

        List<DraftNodeDto> initialized = service.initializeNodes(5L);

        assertThat(initialized).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "DATE", "RECIPIENT", "BODY");
        assertThat(initialized).extracting(DraftNodeDto::content)
                .containsExactly(
                        "在全区重点工作推进会上的讲话",
                        "2026年5月30日",
                        "同志们：",
                        "今天我们召开这次重点工作推进会，主要任务是深入贯彻上级决策部署。"
                );
    }

    @Test
    void initializesAllNonIgnoredReferenceNodesSoNoEditPreviewMatchesOriginalOrder() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
        DraftNodeService service = service(
                draftService,
                new InMemoryDraftNodeRepository(),
                referenceMappingWithUnconfirmedOriginalNodes(),
                referenceStructureProfileWithUnconfirmedOriginalNodes()
        );

        List<DraftNodeDto> initialized = service.initializeNodes(5L);

        assertThat(initialized).extracting(DraftNodeDto::templateNodeKey)
                .containsExactly("reference-title", "reference-subtitle", "reference-body", "reference-footer");
        assertThat(initialized).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "UNKNOWN", "BODY", "STATIC_TEXT");
        assertThat(initialized).extracting(DraftNodeDto::content)
                .containsExactly(
                        "在全区重点工作推进会上的讲话",
                        "政务会议讲话稿测试样例",
                        "今天我们召开这次重点工作推进会，主要任务是深入贯彻上级决策部署。",
                        "测试文档 | 讲话稿范文示例"
                );
        assertThat(initialized).extracting(DraftNodeDto::status)
                .containsExactly("USER_FILLED", "NEEDS_REVIEW", "USER_FILLED", "LOCKED");
    }

    @Test
    void initializesManuallyMappedMetadataRolesAsEditableNodes() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
        DraftNodeService service = service(
                draftService,
                new InMemoryDraftNodeRepository(),
                editableMetadataMapping(),
                editableMetadataStructureProfile()
        );

        List<DraftNodeDto> initialized = service.initializeNodes(5L);

        assertThat(initialized).extracting(DraftNodeDto::role)
                .containsExactly("ISSUING_ORGAN", "DOC_NUMBER", "TABLE_ATTACHMENT");
        assertThat(initialized).extracting(DraftNodeDto::status)
                .containsExactly("USER_FILLED", "USER_FILLED", "USER_FILLED");
    }

    @Test
    void initializesEachBodyNodeFromItsOwnSourceTextInsteadOfDuplicatingLegacyBody() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplateAndOneLegacyBody());
        DraftNodeService service = service(
                draftService,
                new InMemoryDraftNodeRepository(),
                multiBodyPublishedMapping(),
                multiBodyStructureProfile()
        );

        List<DraftNodeDto> initialized = service.initializeNodes(5L);

        assertThat(initialized)
                .filteredOn(node -> "BODY".equals(node.role()))
                .extracting(DraftNodeDto::content)
                .containsExactly("第一段源正文", "第二段源正文", "第三段源正文");
    }

    @Test
    void reinitializePreservesUserEditedNodesWhenRequested() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                multiBodyPublishedMapping(),
                multiBodyStructureProfile()
        );
        List<DraftNodeDto> initialized = service.initializeNodes(5L);
        DraftNodeDto edited = initialized.stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();
        service.updateNode(5L, edited.id(), new UpdateDraftNodeRequest(
                "用户改过的第一段",
                "USER_MODIFIED_AFTER_AI"
        ));

        List<DraftNodeDto> reinitialized = service.reinitializeNodes(
                5L,
                new ReinitializeDraftNodesRequest("FROM_SOURCE_DOCUMENT", true)
        );

        assertThat(reinitialized)
                .filteredOn(node -> node.templateNodeKey().equals(edited.templateNodeKey()))
                .extracting(DraftNodeDto::content)
                .containsExactly("用户改过的第一段");
    }

    @Test
    void reinitializeReplacesUserEditedNodesFromSourceWhenPreserveIsDisabled() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                multiBodyPublishedMapping(),
                multiBodyStructureProfile()
        );
        List<DraftNodeDto> initialized = service.initializeNodes(5L);
        DraftNodeDto edited = initialized.stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();
        service.updateNode(5L, edited.id(), new UpdateDraftNodeRequest(
                "用户改过的第一段",
                "USER_MODIFIED_AFTER_AI"
        ));

        List<DraftNodeDto> reinitialized = service.reinitializeNodes(
                5L,
                new ReinitializeDraftNodesRequest("FROM_SOURCE_DOCUMENT", false)
        );

        assertThat(reinitialized)
                .filteredOn(node -> node.templateNodeKey().equals(edited.templateNodeKey()))
                .extracting(DraftNodeDto::content)
                .containsExactly("第一段源正文");
    }

    @Test
    void listsAndUpdatesNodeContentAfterDraftAccessCheck() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(draftService, new InMemoryDraftNodeRepository(), publishedMapping(), structureProfile());
        DraftNodeDto body = service.initializeNodes(5L).stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();

        DraftNodeDto updated = service.updateNode(5L, body.id(), new UpdateDraftNodeRequest(
                "修改后的正文内容",
                "USER_MODIFIED_AFTER_AI"
        ));

        assertThat(updated.content()).isEqualTo("修改后的正文内容");
        assertThat(updated.status()).isEqualTo("USER_MODIFIED_AFTER_AI");
        assertThat(service.listNodes(5L)).extracting(DraftNodeDto::content)
                .contains("修改后的正文内容");
    }

    @Test
    void rejectsInvalidStatusWhenSavingNode() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(draftService, new InMemoryDraftNodeRepository(), publishedMapping(), structureProfile());
        DraftNodeDto body = service.initializeNodes(5L).stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> service.updateNode(5L, body.id(), new UpdateDraftNodeRequest("正文", "DONE")))
                .isInstanceOfSatisfying(DraftNodeException.class, exception ->
                        assertThat(((DraftNodeException) exception).errorCode()).isEqualTo("DRAFT_NODE_STATUS_INVALID"));
    }

    @Test
    void propagatesDraftAccessFailureBeforeReadingNodes() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenThrow(new DraftNotFoundException(5L));
        InMemoryDraftNodeRepository nodes = new InMemoryDraftNodeRepository();
        FixedMappingRepository mappings = new FixedMappingRepository(publishedMapping());
        DraftNodeService service = new DraftNodeService(
                draftService,
                nodes,
                mappings,
                new FixedStructureProfileRepository(structureProfile())
        );

        assertThatThrownBy(() -> service.initializeNodes(5L))
                .isInstanceOf(DraftNotFoundException.class);
        assertThat(nodes.existsChecks).isZero();
    }

    @Test
    void blocksInitializationWhenPublishedMappingIsMissing() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = new DraftNodeService(
                draftService,
                new InMemoryDraftNodeRepository(),
                new FixedMappingRepository(null),
                new FixedStructureProfileRepository(structureProfile())
        );

        assertThatThrownBy(() -> service.initializeNodes(5L))
                .isInstanceOfSatisfying(DraftNodeException.class, exception ->
                        assertThat(((DraftNodeException) exception).errorCode()).isEqualTo("STRUCTURE_MAPPING_REQUIRED"));
    }

    @Test
    void listsInsertableBodyRolesFromPublishedMappingOnly() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(draftService, new InMemoryDraftNodeRepository(), publishedMapping(), structureProfile());

        List<DraftNodeRoleOptionDto> roles = service.listInsertableRoles(5L);

        assertThat(roles).extracting(DraftNodeRoleOptionDto::role)
                .containsExactly("BODY_HEADING_LEVEL_1", "BODY");
        assertThat(roles).extracting(DraftNodeRoleOptionDto::createsBodyPair)
                .containsExactly(true, false);
    }

    @Test
    void insertsHeadingWithEmptyBodyAfterAnchorAndKeepsSyntheticKeysUnique() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, repository, publishedMapping(), structureProfile());
        List<DraftNodeDto> initialized = service.initializeNodes(5L);
        long anchorNodeId = initialized.stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow()
                .id();

        List<DraftNodeDto> updated = service.insertNode(5L, new InsertDraftNodeRequest(
                "BODY_HEADING_LEVEL_1",
                anchorNodeId,
                "AFTER"
        ));

        assertThat(updated).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "BODY_HEADING_LEVEL_1", "BODY", "BODY_HEADING_LEVEL_1", "BODY", "DATE");
        List<DraftNodeDto> synthetic = updated.stream()
                .filter(node -> node.templateNodeKey().startsWith("synthetic-"))
                .toList();
        assertThat(synthetic).hasSize(2);
        assertThat(synthetic).extracting(DraftNodeDto::role)
                .containsExactly("BODY_HEADING_LEVEL_1", "BODY");
        assertThat(synthetic).extracting(DraftNodeDto::content)
                .containsExactly("", "");
        assertThat(synthetic).extracting(DraftNodeDto::status)
                .containsExactly("EMPTY", "EMPTY");
        assertThat(synthetic).extracting(node -> node.metadata().styleSourceNodeKey())
                .containsExactly("heading-node", "body-node");
        assertThat(synthetic).extracting(node -> node.metadata().groupId())
                .doesNotContain("");
        assertThat(synthetic).extracting(node -> node.metadata().groupId())
                .containsOnly(synthetic.get(0).metadata().groupId());
    }

    @Test
    void renumbersBodyNodesWhenInsertingRepeatedlyAfterSameAnchor() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, repository, publishedMapping(), structureProfile());
        List<DraftNodeDto> initialized = service.initializeNodes(5L);
        long anchorNodeId = initialized.stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow()
                .id();

        service.insertNode(5L, new InsertDraftNodeRequest("BODY_HEADING_LEVEL_1", anchorNodeId, "AFTER"));
        List<DraftNodeDto> updated = service.insertNode(5L, new InsertDraftNodeRequest("BODY_HEADING_LEVEL_1", anchorNodeId, "AFTER"));

        assertThat(updated).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "BODY_HEADING_LEVEL_1", "BODY", "BODY_HEADING_LEVEL_1", "BODY", "BODY_HEADING_LEVEL_1", "BODY", "DATE");
        assertThat(updated).extracting(DraftNodeDto::sortOrder)
                .containsExactly(10, 20, 30, 40, 50, 60, 70, 80);
    }

    @Test
    void insertsSingleBodyAtEndOfBody() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, repository, publishedMapping(), structureProfile());
        service.initializeNodes(5L);

        List<DraftNodeDto> updated = service.insertNode(5L, new InsertDraftNodeRequest(
                "BODY",
                null,
                "END_OF_BODY"
        ));

        assertThat(updated).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "BODY_HEADING_LEVEL_1", "BODY", "BODY", "DATE");
        assertThat(updated.get(3).templateNodeKey()).startsWith("synthetic-");
    }

    private DraftNodeService service(
            DraftService draftService,
            InMemoryDraftNodeRepository nodes,
            StructureMappingProfile mapping,
            DocumentStructureProfile structureProfile
    ) {
        return new DraftNodeService(
                draftService,
                nodes,
                new FixedMappingRepository(mapping),
                new FixedStructureProfileRepository(structureProfile)
        );
    }

    private DraftDetailDto draftWithTemplate() {
        return new DraftDetailDto(5L, "NOTICE", "测试通知", "DRAFT", 9L, List.of(
                new DraftBlockDto(1L, "TITLE", "测试通知", 10),
                new DraftBlockDto(2L, "BODY_PARAGRAPH", "正文内容", 20),
                new DraftBlockDto(3L, "DATE", "2026年5月30日", 30)
        ));
    }

    private DraftDetailDto emptyDraftWithTemplate() {
        return new DraftDetailDto(5L, "NOTICE", "未命名通知", "DRAFT", 9L, List.of());
    }

    private DraftDetailDto draftWithTemplateAndOneLegacyBody() {
        return new DraftDetailDto(5L, "NOTICE", "旧草稿", "DRAFT", 9L, List.of(
                new DraftBlockDto(1L, "BODY_PARAGRAPH", "旧正文块不应被复制", 30)
        ));
    }

    private StructureMappingProfile publishedMapping() {
        return new StructureMappingProfile(
                21L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("title-node", "TITLE", 10),
                        item("heading-node", "BODY_HEADING_LEVEL_1", 20),
                        item("body-node", "BODY", 30),
                        item("date-node", "DATE", 40),
                        new StructureMappingItem("ignored-node", "IGNORE", "", "CONFIRMED", "USER", 1, "", 50)
                ),
                List.of(),
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingProfile publishedMappingV2() {
        return new StructureMappingProfile(
                22L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("title-node", "TITLE", 10),
                        item("heading-node", "BODY_HEADING_LEVEL_1", 20),
                        item("body-node", "BODY", 30),
                        item("date-node", "DATE", 40)
                ),
                List.of(),
                4,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingProfile referencePublishedMapping() {
        return new StructureMappingProfile(
                22L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("reference-title", "TITLE", 10),
                        item("reference-date", "DATE", 20),
                        item("reference-recipient", "RECIPIENT", 30),
                        item("reference-body", "BODY", 40)
                ),
                List.of(),
                4,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingProfile referenceMappingWithUnconfirmedOriginalNodes() {
        return new StructureMappingProfile(
                24L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("reference-title", "TITLE", 10),
                        new StructureMappingItem("reference-subtitle", "UNKNOWN", "", "NEEDS_REVIEW", "RULE", 0.3, "", 20),
                        item("reference-body", "BODY", 30),
                        new StructureMappingItem("reference-footer", "STATIC_TEXT", "", "CONFIRMED", "RULE", 0.8, "", 40),
                        new StructureMappingItem("reference-ignored", "IGNORE", "", "CONFIRMED", "USER", 1, "", 50)
                ),
                List.of(),
                3,
                1,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingProfile multiBodyPublishedMapping() {
        return new StructureMappingProfile(
                23L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("multi-title", "TITLE", 10),
                        item("multi-body-1", "BODY", 30),
                        item("multi-body-2", "BODY", 40),
                        item("multi-body-3", "BODY", 50)
                ),
                List.of(),
                4,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingProfile editableMetadataMapping() {
        return new StructureMappingProfile(
                25L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("organ-node", "ISSUING_ORGAN", 10),
                        item("number-node", "DOC_NUMBER", 20),
                        item("table-node", "TABLE_ATTACHMENT", 30)
                ),
                List.of(),
                3,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private StructureMappingItem item(String nodeKey, String role, int sortOrder) {
        return new StructureMappingItem(nodeKey, role, "", "CONFIRMED", "USER", 1, "", sortOrder);
    }

    private DocumentStructureProfile structureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v1",
                List.of(
                        node("title-node", "TITLE", "模板标题", 10),
                        node("heading-node", "BODY_HEADING_LEVEL_1", "一、工作安排", 20),
                        node("body-node", "BODY", "模板正文", 30),
                        node("date-node", "DATE", "2026年5月30日", 40),
                        node("ignored-node", "IGNORE", "忽略内容", 50)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentStructureProfile referenceStructureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v1",
                List.of(
                        node("reference-title", "TITLE", "在全区重点工作推进会上的讲话", 10),
                        node("reference-date", "DATE", "2026年5月30日", 20),
                        node("reference-recipient", "RECIPIENT", "同志们：", 30),
                        node("reference-body", "BODY", "今天我们召开这次重点工作推进会，主要任务是深入贯彻上级决策部署。", 40)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentStructureProfile referenceStructureProfileWithUnconfirmedOriginalNodes() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v1",
                List.of(
                        node("reference-title", "TITLE", "在全区重点工作推进会上的讲话", 10),
                        node("reference-subtitle", "UNKNOWN", "政务会议讲话稿测试样例", 20),
                        node("reference-body", "BODY", "今天我们召开这次重点工作推进会，主要任务是深入贯彻上级决策部署。", 30),
                        node("reference-footer", "FOOTER_PARAGRAPH", "测试文档 | 讲话稿范文示例", 40),
                        node("reference-ignored", "PARAGRAPH", "忽略内容", 50)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentStructureProfile multiBodyStructureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v2",
                List.of(
                        node("multi-title", "TITLE", "多段正文测试", 10),
                        node("multi-body-1", "BODY", "第一段源正文", 30),
                        node("multi-body-2", "BODY", "第二段源正文", 40),
                        node("multi-body-3", "BODY", "第三段源正文", 50)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentStructureProfile editableMetadataStructureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v2",
                List.of(
                        node("organ-node", "STATIC_TEXT", "示例办公室", 10),
                        node("number-node", "STATIC_TEXT", "示例办〔2026〕5号", 20),
                        node("table-node", "TABLE_PARAGRAPH", "附件表格内容", 30)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private DocumentNode node(String key, String role, String text, int orderIndex) {
        return new DocumentNode(
                key,
                null,
                "PARAGRAPH",
                role,
                text,
                text,
                orderIndex,
                "PARAGRAPH/" + orderIndex,
                null,
                List.of()
        );
    }

    private record FixedStructureProfileRepository(DocumentStructureProfile profile)
            implements DocumentStructureProfileRepository {
        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.of(profile);
        }
    }

    private record FixedMappingRepository(StructureMappingProfile profile) implements StructureMappingRepository {
        @Override
        public StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser) {
            throw new UnsupportedOperationException("save is not used in this test");
        }

        @Override
        public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
            return Optional.ofNullable(profile);
        }

        @Override
        public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
            return profile == null || !status.equals(profile.status()) ? Optional.empty() : Optional.of(profile);
        }

        @Override
        public int nextVersionNo(long templateVersionId) {
            return 1;
        }
    }

    private static final class InMemoryDraftNodeRepository implements DraftNodeRepository {
        private final Map<Long, List<DraftNode>> byDraft = new HashMap<>();
        private long nextId = 100L;
        private int replaceCount;
        private int existsChecks;

        @Override
        public List<DraftNode> findByDraftId(long draftId) {
            return byDraft.getOrDefault(draftId, List.of()).stream()
                    .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                    .toList();
        }

        @Override
        public boolean existsByDraftId(long draftId) {
            existsChecks++;
            return !findByDraftId(draftId).isEmpty();
        }

        @Override
        public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
            replaceCount++;
            List<DraftNode> saved = new ArrayList<>();
            for (DraftNode node : nodes) {
                saved.add(new DraftNode(
                        nextId++,
                        draftId,
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
                        node.metadata(),
                        Instant.now(),
                        Instant.now()
                ));
            }
            byDraft.put(draftId, saved);
            return findByDraftId(draftId);
        }

        @Override
        public List<DraftNode> insertNodes(long draftId, List<DraftNode> nodes) {
            List<DraftNode> saved = new ArrayList<>(findByDraftId(draftId));
            for (DraftNode node : nodes) {
                saved.add(new DraftNode(
                        nextId++,
                        draftId,
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
                        node.metadata(),
                        Instant.now(),
                        Instant.now()
                ));
            }
            byDraft.put(draftId, saved);
            return findByDraftId(draftId);
        }

        @Override
        public List<DraftNode> updateSortOrders(long draftId, Map<Long, Integer> sortOrdersByNodeId) {
            List<DraftNode> nodes = new ArrayList<>(findByDraftId(draftId));
            for (int i = 0; i < nodes.size(); i++) {
                DraftNode node = nodes.get(i);
                Integer sortOrder = sortOrdersByNodeId.get(node.id());
                if (sortOrder == null) {
                    continue;
                }
                nodes.set(i, new DraftNode(
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
                        sortOrder,
                        node.status(),
                        node.formatOverride(),
                        node.metadata(),
                        node.createdAt(),
                        Instant.now()
                ));
            }
            byDraft.put(draftId, nodes);
            return findByDraftId(draftId);
        }

        @Override
        public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
            List<DraftNode> nodes = new ArrayList<>(findByDraftId(draftId));
            for (int i = 0; i < nodes.size(); i++) {
                DraftNode node = nodes.get(i);
                if (node.id() == nodeId) {
                    DraftNode updated = new DraftNode(
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
                            node.metadata(),
                            node.createdAt(),
                            Instant.now()
                    );
                    nodes.set(i, updated);
                    byDraft.put(draftId, nodes);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }
    }
}
