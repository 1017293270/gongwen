package com.gongwen.assistant.template;

import com.gongwen.assistant.template.profile.TemplatePlaceholderProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateUploadServiceTest {
    @Test
    void localStorageSavesTemplateUnderConfiguredDirectory() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("notice-template.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.readString(Path.of(path))).isEqualTo("template");
        assertThat(path).endsWith(".docx");
    }

    @Test
    void localStorageSanitizesPathTraversalFileName() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("..\\..\\evil.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.exists(Path.of(path))).isTrue();
        assertThat(Path.of(path).getFileName().toString()).doesNotContain("..", "\\", "/");
    }

    @Test
    void templateVersionCreateIsTransactional() throws NoSuchMethodException {
        Method create = JdbcTemplateVersionRepository.class.getMethod(
                "create",
                long.class,
                String.class,
                String.class,
                long.class,
                String.class);

        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void templateVersionCreateLocksParentTemplateBeforeVersionCalculation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("for update"), eq(7L)))
                .thenThrow(new IllegalStateException("lock failed"));
        JdbcTemplateVersionRepository repository = new JdbcTemplateVersionRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.create(7L, "notice.docx", "application/docx", 8, "templates/notice.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lock failed");

        verify(jdbcTemplate).queryForList("select id from document_template where id = ? for update", 7L);
        verify(jdbcTemplate, never()).queryForObject(
                contains("document_template_version"),
                eq(Integer.class),
                anyLong());
    }

    @Test
    void markFailedClearsParsedMetadata() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplateVersionRepository repository = new JdbcTemplateVersionRepository(jdbcTemplate);

        repository.markFailed(9L, "TEMPLATE_PARSE_FAILED", "bad template");

        verify(jdbcTemplate).update(
                contains("profile_hash = null"),
                eq("TEMPLATE_PARSE_FAILED"),
                eq("bad template"),
                eq(9L));
    }

    @Test
    void profileRepositoryStoresProfileForTemplateVersion() {
        InMemoryTemplateProfileRepository repository = new InMemoryTemplateProfileRepository();
        TemplateProfile profile = new TemplateProfile(
                1,
                List.of(),
                List.of(new TemplatePlaceholderProfile("title", "PARAGRAPH", "paragraph-0", null, null, false)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        repository.save(9L, profile, "hash-1");

        assertThat(repository.findByTemplateVersionId(9L)).contains(profile);
    }

    private static class InMemoryTemplateProfileRepository implements TemplateProfileRepository {
        private final Map<Long, TemplateProfile> profiles = new HashMap<>();

        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            profiles.put(templateVersionId, profile);
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profiles.get(templateVersionId));
        }
    }
}
