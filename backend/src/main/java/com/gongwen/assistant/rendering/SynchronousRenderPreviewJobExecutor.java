package com.gongwen.assistant.rendering;

import org.springframework.stereotype.Component;

@Component
public class SynchronousRenderPreviewJobExecutor implements RenderPreviewJobExecutor {
    @Override
    public void submit(Runnable job) {
        job.run();
    }
}
