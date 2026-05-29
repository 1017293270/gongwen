package com.gongwen.assistant.draft;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftServiceTest {
    private final InMemoryDraftRepository repository = new InMemoryDraftRepository();
    private final DraftService service = new DraftService(
            repository,
            Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void createsNoticeDraftWithDefaultBlocks() {
        DraftDetailDto draft = service.createDraft(new CreateDraftRequest("NOTICE", "测试通知"));

        assertThat(draft.id()).isEqualTo(1L);
        assertThat(draft.documentTypeCode()).isEqualTo("NOTICE");
        assertThat(draft.title()).isEqualTo("测试通知");
        assertThat(draft.blocks()).extracting(DraftBlockDto::blockType)
                .containsExactly("TITLE", "RECIPIENT", "BODY_PARAGRAPH", "ATTACHMENT", "SIGNATURE", "DATE");
        assertThat(draft.blocks()).extracting(DraftBlockDto::content)
                .containsExactly("测试通知", "各部门、各直属单位", "", "无", "办公室", "2026年5月25日");
    }

    @Test
    void updatesBlocksAndReadsThemBack() {
        DraftDetailDto draft = service.createDraft(new CreateDraftRequest("NOTICE", "测试通知"));

        DraftDetailDto updated = service.updateBlocks(draft.id(), new UpdateDraftBlocksRequest(List.of(
                new DraftBlockUpdateRequest("TITLE", "新标题", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各单位", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "正文内容", 30),
                new DraftBlockUpdateRequest("ATTACHMENT", "附件：材料清单", 40),
                new DraftBlockUpdateRequest("SIGNATURE", "综合办公室", 50),
                new DraftBlockUpdateRequest("DATE", "2026年5月26日", 60)
        )));

        assertThat(updated.title()).isEqualTo("新标题");
        assertThat(service.getDraft(draft.id()).blocks()).extracting(DraftBlockDto::content)
                .containsExactly("新标题", "各单位", "正文内容", "附件：材料清单", "综合办公室", "2026年5月26日");
    }

    @Test
    void listsDraftsFilteredByDocumentType() {
        service.createDraft(new CreateDraftRequest("NOTICE", "通知草稿"));
        service.createDraft(new CreateDraftRequest("REQUEST", "请示草稿"));

        List<DraftSummaryDto> drafts = service.listDrafts("REQUEST");

        assertThat(drafts).extracting(DraftSummaryDto::title)
                .containsExactly("请示草稿");
        assertThat(drafts).extracting(DraftSummaryDto::documentTypeCode)
                .containsExactly("REQUEST");
    }

    @Test
    void renamesDraftAndKeepsBlocks() {
        DraftDetailDto draft = service.createDraft(new CreateDraftRequest("NOTICE", "原草稿名称"));

        DraftDetailDto renamed = service.updateTitle(draft.id(), new UpdateDraftTitleRequest("已重命名通知草稿"));

        assertThat(renamed.title()).isEqualTo("已重命名通知草稿");
        assertThat(renamed.blocks()).extracting(DraftBlockDto::blockType)
                .containsExactly("TITLE", "RECIPIENT", "BODY_PARAGRAPH", "ATTACHMENT", "SIGNATURE", "DATE");
        assertThat(service.listDrafts("NOTICE")).extracting(DraftSummaryDto::title)
                .containsExactly("已重命名通知草稿");
    }

    @Test
    void failsWhenDraftDoesNotExist() {
        assertThatThrownBy(() -> service.getDraft(99L))
                .isInstanceOf(DraftNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deletesDraftAndRemovesItFromDocumentTypeList() {
        DraftDetailDto draft = service.createDraft(new CreateDraftRequest("NOTICE", "待删除草稿"));

        service.deleteDraft(draft.id());

        assertThat(service.listDrafts("NOTICE")).isEmpty();
        assertThatThrownBy(() -> service.getDraft(draft.id()))
                .isInstanceOf(DraftNotFoundException.class);
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private final List<DraftDetailDto> drafts = new ArrayList<>();
        private long nextDraftId = 1L;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            DraftDetailDto draft = new DraftDetailDto(nextDraftId++, documentTypeCode, title, "DRAFT", toDtos(blocks));
            drafts.add(draft);
            return draft;
        }

        @Override
        public DraftDetailDto findById(long id) {
            return drafts.stream()
                    .filter(draft -> draft.id() == id)
                    .findFirst()
                    .orElseThrow(() -> new DraftNotFoundException(id));
        }

        @Override
        public List<DraftSummaryDto> listByDocumentType(String documentTypeCode) {
            return drafts.stream()
                    .filter(draft -> draft.documentTypeCode().equals(documentTypeCode))
                    .map(draft -> new DraftSummaryDto(
                            draft.id(),
                            draft.documentTypeCode(),
                            draft.title(),
                            draft.status(),
                            draft.templateVersionId(),
                            "2026-05-27T08:00:00Z"
                    ))
                    .toList();
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            DraftDetailDto existing = findById(id);
            String title = blocks.stream()
                    .filter(block -> "TITLE".equals(block.blockType()))
                    .findFirst()
                    .map(DraftBlockUpdateRequest::content)
                    .orElse(existing.title());
            DraftDetailDto updated = new DraftDetailDto(id, existing.documentTypeCode(), title, existing.status(), toDtos(blocks));
            drafts.replaceAll(draft -> draft.id() == id ? updated : draft);
            return updated;
        }

        @Override
        public DraftDetailDto updateTitle(long id, String title) {
            DraftDetailDto existing = findById(id);
            DraftDetailDto updated = new DraftDetailDto(
                    id,
                    existing.documentTypeCode(),
                    title,
                    existing.status(),
                    existing.templateVersionId(),
                    existing.blocks()
            );
            drafts.replaceAll(draft -> draft.id() == id ? updated : draft);
            return updated;
        }

        @Override
        public void deleteById(long id) {
            findById(id);
            drafts.removeIf(draft -> draft.id() == id);
        }

        private List<DraftBlockDto> toDtos(List<DraftBlockUpdateRequest> blocks) {
            List<DraftBlockDto> result = new ArrayList<>();
            long id = 1L;
            for (DraftBlockUpdateRequest block : blocks) {
                result.add(new DraftBlockDto(id++, block.blockType(), block.content(), block.sortOrder()));
            }
            return result;
        }
    }
}
