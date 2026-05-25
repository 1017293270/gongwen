package com.gongwen.assistant.draft;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftServiceTest {
    private final InMemoryDraftRepository repository = new InMemoryDraftRepository();
    private final DraftService service = new DraftService(repository);

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
    void failsWhenDraftDoesNotExist() {
        assertThatThrownBy(() -> service.getDraft(99L))
                .isInstanceOf(DraftNotFoundException.class)
                .hasMessageContaining("99");
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private DraftDetailDto draft;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            this.draft = new DraftDetailDto(1L, documentTypeCode, title, "DRAFT", toDtos(blocks));
            return draft;
        }

        @Override
        public DraftDetailDto findById(long id) {
            if (draft == null || draft.id() != id) {
                throw new DraftNotFoundException(id);
            }
            return draft;
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            DraftDetailDto existing = findById(id);
            String title = blocks.stream()
                    .filter(block -> "TITLE".equals(block.blockType()))
                    .findFirst()
                    .map(DraftBlockUpdateRequest::content)
                    .orElse(existing.title());
            this.draft = new DraftDetailDto(id, existing.documentTypeCode(), title, existing.status(), toDtos(blocks));
            return draft;
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
