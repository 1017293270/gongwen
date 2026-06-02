package com.gongwen.assistant.template;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTemplateRepositoryTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcTemplateRepository repository = new JdbcTemplateRepository(jdbcTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void returnsExistingTemplateWhenNameAlreadyExistsForSameDocumentType() {
        TemplateSummary existing = new TemplateSummary(7L, "测试2", "NOTICE", "ACTIVE");
        when(jdbcTemplate.query(startsWith("insert into document_template"), any(RowMapper.class), eq("测试2"), eq("NOTICE")))
                .thenReturn(List.of());
        when(jdbcTemplate.query(startsWith("select id, template_name"), any(RowMapper.class), eq("测试2")))
                .thenReturn(List.of(existing));

        TemplateSummary result = repository.create(" 测试2 ", "NOTICE");

        assertThat(result).isEqualTo(existing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsExistingTemplateNameFromAnotherDocumentType() {
        TemplateSummary existing = new TemplateSummary(7L, "测试2", "REPORT", "ACTIVE");
        when(jdbcTemplate.query(startsWith("insert into document_template"), any(RowMapper.class), eq("测试2"), eq("NOTICE")))
                .thenReturn(List.of());
        when(jdbcTemplate.query(startsWith("select id, template_name"), any(RowMapper.class), eq("测试2")))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> repository.create("测试2", "NOTICE"))
                .isInstanceOf(TemplateException.class)
                .hasMessage("Template name already exists in another document type");
    }
    @Test
    @SuppressWarnings("unchecked")
    void rejectsDeletingTemplateWhenDraftsStillUseIt() {
        TemplateSummary existing = new TemplateSummary(7L, "模板", "NOTICE", "ACTIVE");
        when(jdbcTemplate.query(startsWith("select id, template_name"), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of(existing));
        when(jdbcTemplate.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(7L), eq(7L)))
                .thenReturn(2);

        assertThatThrownBy(() -> repository.deleteById(7L))
                .isInstanceOf(TemplateException.class)
                .hasMessage("模板已被草稿使用，不能直接删除；请先删除相关草稿或更换草稿模板后再删除模板");

        verify(jdbcTemplate, never()).update(startsWith("delete from document_template"), eq(7L));
    }
}
