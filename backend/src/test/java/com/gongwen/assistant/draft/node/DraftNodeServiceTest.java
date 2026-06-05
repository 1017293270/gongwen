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
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateLineSpacingProfile;
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
    void initializesWorkbenchSkeletonWithTemplateHeadingsOnly() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(draftService, new InMemoryDraftNodeRepository(), publishedMapping(), structureProfile());

        List<DraftNodeDto> initialized = service.initializeNodes(
                5L,
                new InitializeDraftNodesRequest("TEMPLATE_HEADINGS_ONLY")
        );

        assertThat(initialized).extracting(DraftNodeDto::role)
                .containsExactly("TITLE", "BODY_HEADING_LEVEL_1", "BODY", "DATE");
        assertThat(initialized).extracting(DraftNodeDto::content)
                .containsExactly("模板标题", "一、工作安排", "", "2026年5月30日");
        assertThat(bodyNode(initialized).status()).isEqualTo("EMPTY");
    }

    @Test
    void appliesOutlineByReplacingOnlyBodyStructureNodes() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(draftService, repository, publishedMapping(), structureProfile());
        service.initializeNodes(5L);

        ApplyOutlineResponse response = service.applyOutline(5L, new ApplyOutlineRequest(
                null,
                "测试通知",
                List.of(
                        new com.gongwen.assistant.ai.AiOutlineSection("一、总体要求", List.of("说明背景"), 1, List.of("materialId=1")),
                        new com.gongwen.assistant.ai.AiOutlineSection("（一）重点任务", List.of("说明任务"), 2, List.of()),
                        new com.gongwen.assistant.ai.AiOutlineSection("1. 责任分工", List.of("说明分工"), 3, List.of())
                )
        ));

        assertThat(response.nodes()).extracting(DraftNodeDto::role)
                .containsExactly(
                        "TITLE",
                        "BODY_HEADING_LEVEL_1",
                        "BODY",
                        "BODY_HEADING_LEVEL_2",
                        "BODY",
                        "BODY_HEADING_LEVEL_3",
                        "BODY",
                        "DATE"
                );
        assertThat(response.nodes())
                .filteredOn(node -> node.role().startsWith("BODY_HEADING_LEVEL_"))
                .extracting(DraftNodeDto::content)
                .containsExactly("一、总体要求", "（一）重点任务", "1. 责任分工");
        assertThat(response.nodes())
                .filteredOn(node -> "BODY".equals(node.role()))
                .extracting(DraftNodeDto::content)
                .containsExactly("", "", "");
        assertThat(response.sectionTargets()).hasSize(3);
        assertThat(response.sectionTargets()).allSatisfy(target -> assertThat(target.bodyNodeId()).isNotNull());
        assertThat(response.nodes())
                .filteredOn(node -> "DATE".equals(node.role()))
                .extracting(DraftNodeDto::id)
                .containsExactly(103L);

        List<DraftNode> allNodes = repository.findByDraftId(5L);
        assertThat(allNodes)
                .filteredOn(node -> "heading-node".equals(node.templateNodeKey()) || "body-node".equals(node.templateNodeKey()))
                .extracting(DraftNode::status)
                .containsOnly("DELETED");
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic())
                .hasSize(6)
                .allSatisfy(node -> {
                    assertThat(node.metadata().anchorTemplateNodeKey()).isEqualTo("title-node");
                    assertThat(node.metadata().insertPosition()).isEqualTo("AFTER");
                    assertThat(node.metadata().groupId()).isNotBlank();
                });
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("body-node"));
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_1".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("heading-node"));
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_2".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("body-node"));
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_3".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("body-node"));
    }

    @Test
    void applyOutlineUsesHeadingNumberingWhenTemplateHeadingRolesAreMisclassified() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                misclassifiedHeadingMapping(),
                misclassifiedHeadingStructureProfile()
        );
        service.initializeNodes(5L);

        service.applyOutline(5L, new ApplyOutlineRequest(
                null,
                "测试通知",
                List.of(
                        new com.gongwen.assistant.ai.AiOutlineSection("一、总体要求", List.of(), 1, List.of()),
                        new com.gongwen.assistant.ai.AiOutlineSection("（一）组织领导", List.of(), 2, List.of()),
                        new com.gongwen.assistant.ai.AiOutlineSection("1. 子任务细节", List.of(), 3, List.of())
                )
        ));

        List<DraftNode> allNodes = repository.findByDraftId(5L);
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_1".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("level1-node"));
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_2".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("level2-node"));
        assertThat(allNodes)
                .filteredOn(node -> node.metadata().synthetic() && "BODY_HEADING_LEVEL_3".equals(node.role()))
                .allSatisfy(node -> assertThat(node.metadata().styleSourceNodeKey()).isEqualTo("level3-node"));
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
    void listNodesReturnsBaseAndEffectiveFormattingFromSourceAndTemplateOverride() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(
                draftService,
                new InMemoryDraftNodeRepository(),
                publishedMapping(),
                structureProfile(),
                Map.of("body-node", templateBodyFormattingOverride())
        );

        DraftNodeDto initializedBody = bodyNode(service.initializeNodes(5L));
        DraftNodeDto listedBody = bodyNode(service.listNodes(5L));

        assertThat(initializedBody.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(initializedBody.effectiveFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(listedBody.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(listedBody.effectiveFormatting()).isEqualTo(expectedBaseBodyFormatting());
    }

    @Test
    void listNodesMergesDraftFormatOverrideIntoEffectiveFormattingOnly() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        DraftNodeService service = service(
                draftService,
                new InMemoryDraftNodeRepository(),
                publishedMapping(),
                structureProfile(),
                Map.of("body-node", templateBodyFormattingOverride())
        );
        DraftNodeDto body = bodyNode(service.initializeNodes(5L));

        DraftNodeDto saved = service.saveFormatOverride(5L, body.id(), new DraftNodeFormatOverride(
                "DraftKai",
                null,
                18.0,
                true,
                "CENTER",
                null,
                null,
                null,
                null,
                null
        ));
        DraftNodeDto listed = service.listNodes(5L).stream()
                .filter(node -> node.id() == body.id())
                .findFirst()
                .orElseThrow();

        assertThat(saved.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(saved.effectiveFormatting().eastAsiaFontFamily()).isEqualTo("DraftKai");
        assertThat(saved.effectiveFormatting().fontFamily()).isEqualTo("DraftKai");
        assertThat(saved.effectiveFormatting().fontSizeHalfPoints()).isEqualTo(36);
        assertThat(saved.effectiveFormatting().bold()).isTrue();
        assertThat(saved.effectiveFormatting().alignment()).isEqualTo("CENTER");
        assertThat(listed.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(listed.effectiveFormatting()).isEqualTo(saved.effectiveFormatting());
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
                new FixedStructureProfileRepository(structureProfile()),
                formattingResolver(structureProfile(), Map.of())
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
                new FixedStructureProfileRepository(structureProfile()),
                formattingResolver(structureProfile(), Map.of())
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

    @Test
    void syntheticBodyNodeUsesStyleSourceNodeKeyForFormatting() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                publishedMapping(),
                structureProfile(),
                Map.of("body-node", templateBodyFormattingOverride())
        );
        service.initializeNodes(5L);

        DraftNodeDto syntheticBody = service.insertNode(5L, new InsertDraftNodeRequest(
                        "BODY",
                        null,
                        "END_OF_BODY"
                )).stream()
                .filter(node -> node.metadata().synthetic())
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();

        assertThat(syntheticBody.metadata().styleSourceNodeKey()).isEqualTo("body-node");
        assertThat(syntheticBody.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(syntheticBody.effectiveFormatting()).isEqualTo(expectedBaseBodyFormatting());
    }

    @Test
    void legacySyntheticHeadingWithCrossLevelStyleSourceUsesBodyFormatting() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                publishedMapping(),
                structureProfile(),
                Map.of("body-node", templateBodyFormattingOverride())
        );
        service.initializeNodes(5L);
        repository.insertNodes(5L, List.of(new DraftNode(
                0,
                5L,
                100L,
                "outline-legacy-heading",
                null,
                "PARAGRAPH",
                "BODY_HEADING_LEVEL_2",
                "BODY_HEADING_LEVEL_2",
                "BODY_HEADING_LEVEL_2",
                "（一）旧二级标题",
                35,
                "USER_FILLED",
                DraftNodeFormatOverride.empty(),
                DraftNodeMetadata.synthetic(null, "title-node", "AFTER", "legacy-heading", "heading-node"),
                null,
                null
        )));

        DraftNodeDto legacyHeading = service.listNodes(5L).stream()
                .filter(node -> "（一）旧二级标题".equals(node.content()))
                .findFirst()
                .orElseThrow();

        assertThat(legacyHeading.metadata().styleSourceNodeKey()).isEqualTo("heading-node");
        assertThat(legacyHeading.baseFormatting()).isEqualTo(expectedBaseBodyFormatting());
        assertThat(legacyHeading.effectiveFormatting()).isEqualTo(expectedBaseBodyFormatting());
    }

    @Test
    void legacySyntheticHeadingWithMisclassifiedExactStyleSourceUsesNumberedLevelSource() {
        DraftService draftService = mock(DraftService.class);
        when(draftService.getDraft(5L)).thenReturn(draftWithTemplate());
        InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
        DraftNodeService service = service(
                draftService,
                repository,
                misclassifiedHeadingMapping(),
                misclassifiedHeadingStructureProfile()
        );
        service.initializeNodes(5L);
        repository.insertNodes(5L, List.of(new DraftNode(
                0,
                5L,
                100L,
                "outline-legacy-heading",
                null,
                "PARAGRAPH",
                "BODY_HEADING_LEVEL_2",
                "BODY_HEADING_LEVEL_2",
                "BODY_HEADING_LEVEL_2",
                "（一）旧二级标题",
                35,
                "USER_FILLED",
                DraftNodeFormatOverride.empty(),
                DraftNodeMetadata.synthetic(null, "title-node", "AFTER", "legacy-heading", "level3-node"),
                null,
                null
        )));

        DraftNodeDto legacyHeading = service.listNodes(5L).stream()
                .filter(node -> "（一）旧二级标题".equals(node.content()))
                .findFirst()
                .orElseThrow();

        assertThat(legacyHeading.metadata().styleSourceNodeKey()).isEqualTo("level3-node");
        assertThat(legacyHeading.baseFormatting().alignment()).isEqualTo("LEFT");
        assertThat(legacyHeading.baseFormatting().indentationFirstLine()).isEqualTo(280);
    }

    private DraftNodeService service(
            DraftService draftService,
            InMemoryDraftNodeRepository nodes,
            StructureMappingProfile mapping,
            DocumentStructureProfile structureProfile
    ) {
        return service(draftService, nodes, mapping, structureProfile, Map.of());
    }

    private DraftNodeService service(
            DraftService draftService,
            InMemoryDraftNodeRepository nodes,
            StructureMappingProfile mapping,
            DocumentStructureProfile structureProfile,
            Map<String, TemplateStructureFormattingProfile> formattingOverrides
    ) {
        return new DraftNodeService(
                draftService,
                nodes,
                new FixedMappingRepository(mapping),
                new FixedStructureProfileRepository(structureProfile),
                formattingResolver(structureProfile, formattingOverrides)
        );
    }

    private DraftNodeFormattingResolver formattingResolver(
            DocumentStructureProfile structureProfile,
            Map<String, TemplateStructureFormattingProfile> formattingOverrides
    ) {
        return new DraftNodeFormattingResolver(
                new FixedStructureProfileRepository(structureProfile),
                new FixedFormattingRepository(formattingOverrides),
                new TemplateEffectiveFormattingService()
        );
    }

    private DraftNodeDto bodyNode(List<DraftNodeDto> nodes) {
        return nodes.stream()
                .filter(node -> "BODY".equals(node.role()))
                .findFirst()
                .orElseThrow();
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

    private StructureMappingProfile misclassifiedHeadingMapping() {
        return new StructureMappingProfile(
                23L,
                9L,
                2,
                "PUBLISHED",
                List.of(
                        item("title-node", "TITLE", 10),
                        item("level1-node", "BODY_HEADING_LEVEL_1", 20),
                        item("level2-node", "BODY_HEADING_LEVEL_1", 30),
                        item("level3-node", "BODY_HEADING_LEVEL_2", 40),
                        item("body-node", "BODY", 50),
                        item("date-node", "DATE", 60)
                ),
                List.of(),
                6,
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

    private DocumentStructureProfile misclassifiedHeadingStructureProfile() {
        return new DocumentStructureProfile(
                1,
                "hash",
                "document-structure-v1",
                List.of(
                        node("title-node", "TITLE", "模板标题", 10),
                        node("level1-node", "BODY_HEADING_LEVEL_1", "一、模板一级", 20),
                        node("level2-node", "BODY_HEADING_LEVEL_1", "（一）模板二级", 30),
                        node("level3-node", "BODY_HEADING_LEVEL_2", "1. 模板三级", 40),
                        node("body-node", "BODY", "模板正文", 50),
                        node("date-node", "DATE", "2026年5月30日", 60)
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
                sourceFormattingFor(key),
                List.of()
        );
    }

    private TemplateStructureFormattingProfile sourceFormattingFor(String key) {
        if ("body-node".equals(key)) {
            return new TemplateStructureFormattingProfile(
                    "SourceFangSong",
                    30,
                    false,
                    "LEFT",
                    560,
                    150,
                    20,
                    40,
                    null,
                    "SourceFangSong",
                    "SourceRoman",
                    new TemplateLineSpacingProfile("AUTO", null, 150)
            );
        }
        if ("level2-node".equals(key)) {
            return new TemplateStructureFormattingProfile(
                    "SourceKai",
                    30,
                    false,
                    "LEFT",
                    280,
                    150,
                    0,
                    0,
                    null,
                    "SourceKai",
                    "SourceRoman",
                    new TemplateLineSpacingProfile("AUTO", null, 150)
            );
        }
        if ("level3-node".equals(key)) {
            return new TemplateStructureFormattingProfile(
                    "SourceHei",
                    30,
                    true,
                    "CENTER",
                    0,
                    150,
                    0,
                    0,
                    null,
                    "SourceHei",
                    "SourceRoman",
                    new TemplateLineSpacingProfile("AUTO", null, 150)
            );
        }
        return null;
    }

    private TemplateStructureFormattingProfile templateBodyFormattingOverride() {
        return new TemplateStructureFormattingProfile(
                "TemplateFangSong",
                32,
                null,
                "BOTH",
                720,
                180,
                null,
                80,
                null,
                "TemplateFangSong",
                null,
                new TemplateLineSpacingProfile("AUTO", null, 180)
        );
    }

    private TemplateStructureFormattingProfile expectedBaseBodyFormatting() {
        return new TemplateStructureFormattingProfile(
                "TemplateFangSong",
                32,
                false,
                "BOTH",
                720,
                180,
                20,
                80,
                null,
                "TemplateFangSong",
                "SourceRoman",
                new TemplateLineSpacingProfile("AUTO", null, 180)
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

    private record FixedFormattingRepository(Map<String, TemplateStructureFormattingProfile> overrides)
            implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return overrides;
        }

        @Override
        public void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting) {
            throw new UnsupportedOperationException("saveOverride is not used in this test");
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
        public void deleteBodyStructureNodes(long draftId) {
            byDraft.put(
                    draftId,
                    findByDraftId(draftId).stream()
                            .map(node -> "BODY".equals(node.role()) || node.role().startsWith("BODY_HEADING_LEVEL_")
                                    ? withStatus(node, "DELETED")
                                    : node)
                            .toList()
            );
        }

        private DraftNode withStatus(DraftNode node, String status) {
            return new DraftNode(
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
                    node.formatOverride(),
                    node.metadata(),
                    node.createdAt(),
                    Instant.now()
            );
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

        @Override
        public Optional<DraftNode> updateFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride override, String status) {
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
                            node.content(),
                            node.sortOrder(),
                            status,
                            override,
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
