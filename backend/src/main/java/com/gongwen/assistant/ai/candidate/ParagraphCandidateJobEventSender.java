package com.gongwen.assistant.ai.candidate;

@FunctionalInterface
interface ParagraphCandidateJobEventSender {
    void send(ParagraphCandidateJobEvent event);
}
