package com.gongwen.assistant.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiGenerationTraceRepository implements AiGenerationTraceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiGenerationTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AiGenerationTrace trace) {
        jdbcTemplate.update("""
                        insert into ai_generation_trace (
                            id,
                            draft_id,
                            task_type,
                            provider,
                            model_name,
                            status,
                            prompt_version,
                            input_summary,
                            output_summary,
                            error_code,
                            error_message,
                            latency_ms
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                trace.id(),
                trace.draftId(),
                trace.taskType(),
                trace.provider(),
                trace.modelName(),
                trace.status(),
                trace.promptVersion(),
                trace.inputSummary(),
                trace.outputSummary(),
                trace.errorCode(),
                trace.errorMessage(),
                trace.latencyMs());
    }
}
