package com.gongwen.assistant.rendering;

public interface RenderPreviewJobExecutor {
    void submit(Runnable job);
}
