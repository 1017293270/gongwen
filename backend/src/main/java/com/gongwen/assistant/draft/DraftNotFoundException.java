package com.gongwen.assistant.draft;

public class DraftNotFoundException extends RuntimeException {
    public DraftNotFoundException(long draftId) {
        super("草稿不存在：" + draftId);
    }
}
