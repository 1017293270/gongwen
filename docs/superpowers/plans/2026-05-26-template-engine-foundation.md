# Template Engine Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the T1 backend foundation for a Word-style-first template engine: versioned template upload, raw Word profile extraction, profile persistence, and template validation warnings.

**Architecture:** Keep the current `DocxPlaceholderParser` and `/api/templates/parse` compatible, but add a new profile pipeline under `com.gongwen.assistant.template.profile`. Uploading a `.docx` stores an immutable template version, parses a `TemplateProfile`, persists profile JSON and validation results, and returns a summary for future template admin UI, quality checks, and export planning.

**Tech Stack:** Java 21, Spring Boot 3.3, Apache POI XWPF, PostgreSQL JSONB, Flyway, JUnit 5, AssertJ, MockMvc.

---

## Scope

This plan implements only the first stage of `docs/superpowers/specs/2026-05-26-template-engine-design.md`: **T1 模板引擎底座**.

Included:

- Versioned `.docx` template upload.
- Local template file storage.
- Profile parser for placeholders, styles, sections, tables, headers/footers, numbering presence, and media presence.
- Cross-run placeholder detection.
- JSONB persistence of `TemplateProfile`.
- Template validation warnings.
- Focused backend tests.
- Documentation updates for the new API and phase ordering.

Not included:

- Export engine upgrade.
- Template management UI.
- Workbench template selection.
- P8 quality panel integration.
- Full visual editing of Word styles.

## File Structure

Create:

- `backend/src/main/resources/db/migration/V7__template_engine_foundation.sql`  
  Adds `document_template_version`, `template_profile`, `template_block_mapping`, `template_rule`, and `template_validation_result`.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateProperties.java`  
  Holds local template storage path and upload limits.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateStorage.java`  
  Interface for storing uploaded template files.

- `backend/src/main/java/com/gongwen/assistant/template/LocalTemplateStorage.java`  
  Local filesystem implementation.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateVersion.java`  
  Immutable template version domain record.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateVersionRepository.java`  
  Repository interface.

- `backend/src/main/java/com/gongwen/assistant/template/JdbcTemplateVersionRepository.java`  
  JDBC implementation for template version insert/read.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfile.java`  
  Root profile record.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateStyleProfile.java`  
  Word style summary.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplatePlaceholderProfile.java`  
  Placeholder position summary.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateSectionProfile.java`  
  Section and page setup summary.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateTableProfile.java`  
  Table summary.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateMediaProfile.java`  
  Media summary.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateValidationItem.java`  
  Validation warning/error record.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`  
  Apache POI parser that produces `TemplateProfile`.

- `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileRepository.java`  
  Repository interface.

- `backend/src/main/java/com/gongwen/assistant/template/profile/JdbcTemplateProfileRepository.java`  
  JSONB persistence implementation.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java`  
  Orchestrates validation, storage, version creation, parsing, profile persistence, and response summary.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadResponse.java`  
  API response shape.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateException.java`  
  Normalized template errors.

- `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`  
  Parser focused tests.

- `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java`  
  Service focused tests.

- `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadControllerTest.java`  
  Controller focused tests.

Modify:

- `backend/src/main/java/com/gongwen/assistant/template/TemplateConfiguration.java`  
  Register `TemplateProfileParser` and bind `TemplateProperties`.

- `backend/src/main/java/com/gongwen/assistant/template/TemplateController.java`  
  Add `POST /api/templates/upload` and `GET /api/templates/versions/{versionId}/profile`.

- `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`  
  Add factories for cross-run placeholders, styled paragraphs, headers, tables, and media-free section setup.

- `backend/src/main/resources/application.yml`  
  Add template storage config.

- `.env.example`  
  Add `GONGWEN_TEMPLATE_STORAGE_DIR`.

- `AGENTS.md`  
  Update current template engine conventions after implementation.

- `docs/PROJECT_TASKS.md`  
  Insert P8A template engine foundation into the development queue after implementation.

---

### Task 1: Add Template Engine Schema

**Files:**

- Create: `backend/src/main/resources/db/migration/V7__template_engine_foundation.sql`

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V7__template_engine_foundation.sql`:

```sql
create table document_template_version (
    id bigserial primary key,
    template_id bigint not null references document_template(id) on delete cascade,
    version_no integer not null,
    original_file_name varchar(255) not null,
    content_type varchar(150) not null,
    file_size_bytes bigint not null,
    file_path varchar(500) not null,
    profile_hash varchar(128),
    parse_status varchar(30) not null default 'PENDING',
    parse_error_code varchar(100),
    parse_error_message varchar(500),
    created_by bigint,
    created_at timestamptz not null default now(),
    unique (template_id, version_no)
);

create table template_profile (
    id bigserial primary key,
    template_version_id bigint not null unique references document_template_version(id) on delete cascade,
    schema_version integer not null,
    profile_json jsonb not null,
    created_at timestamptz not null default now()
);

create table template_block_mapping (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    block_type varchar(50) not null,
    paragraph_role varchar(80),
    placeholder_key varchar(100),
    style_id varchar(150),
    style_name varchar(200),
    anchor_paragraph_key varchar(100),
    repeat_mode varchar(50) not null default 'SINGLE',
    required boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table template_rule (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    target_type varchar(80) not null,
    target_key varchar(150) not null,
    rule_type varchar(80) not null,
    expected_value jsonb not null,
    severity varchar(30) not null default 'WARNING',
    source varchar(50) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table template_validation_result (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    severity varchar(30) not null,
    code varchar(100) not null,
    message varchar(500) not null,
    target_type varchar(80),
    target_key varchar(150),
    details_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index idx_template_version_template_id on document_template_version(template_id);
create index idx_template_version_parse_status on document_template_version(parse_status);
create index idx_template_block_mapping_version on template_block_mapping(template_version_id);
create index idx_template_rule_version on template_rule(template_version_id);
create index idx_template_validation_version on template_validation_result(template_version_id);
```

- [ ] **Step 2: Run backend focused migration smoke through existing tests**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.parser.DocxPlaceholderParserTest"
```

Expected: existing parser tests still pass and Flyway migration parsing does not break application context in later tasks.

- [ ] **Step 3: Commit**

```powershell
git add backend/src/main/resources/db/migration/V7__template_engine_foundation.sql
git commit -m "feat: add template engine schema"
```

---

### Task 2: Add Template Storage and Version Persistence

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateProperties.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateStorage.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/LocalTemplateStorage.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateVersion.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateVersionRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/JdbcTemplateVersionRepository.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`

- [ ] **Step 1: Write failing storage test in service test file**

Create `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java` with only this first test and support fakes:

```java
package com.gongwen.assistant.template;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateUploadServiceTest {
    @Test
    void localStorageSavesTemplateUnderConfiguredDirectory() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("通知模板.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.readString(Path.of(path))).isEqualTo("template");
        assertThat(path).endsWith(".docx");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: FAIL because `LocalTemplateStorage` and `TemplateProperties` do not exist.

- [ ] **Step 3: Implement properties and storage**

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateProperties.java`:

```java
package com.gongwen.assistant.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gongwen.template")
public record TemplateProperties(
        String storageDir,
        long maxUploadMb
) {
    public TemplateProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "storage/templates";
        }
        if (maxUploadMb <= 0) {
            maxUploadMb = 20;
        }
    }
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateStorage.java`:

```java
package com.gongwen.assistant.template;

import java.io.IOException;

public interface TemplateStorage {
    String save(String originalFileName, String fileExtension, byte[] content) throws IOException;
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/LocalTemplateStorage.java`:

```java
package com.gongwen.assistant.template;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
public class LocalTemplateStorage implements TemplateStorage {
    private final TemplateProperties properties;

    public LocalTemplateStorage(TemplateProperties properties) {
        this.properties = properties;
    }

    @Override
    public String save(String originalFileName, String fileExtension, byte[] content) throws IOException {
        Path directory = Path.of(properties.storageDir()).normalize();
        Files.createDirectories(directory);
        String safeBaseName = StringUtils.cleanPath(originalFileName).replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeBaseName;
        if (!fileName.endsWith("." + fileExtension)) {
            fileName = fileName + "." + fileExtension;
        }
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("Invalid template storage path");
        }
        Files.write(target, content);
        return target.toString();
    }
}
```

- [ ] **Step 4: Add version domain and repository**

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateVersion.java`:

```java
package com.gongwen.assistant.template;

import java.time.Instant;

public record TemplateVersion(
        long id,
        long templateId,
        int versionNo,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String filePath,
        String profileHash,
        String parseStatus,
        String parseErrorCode,
        String parseErrorMessage,
        Instant createdAt
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateVersionRepository.java`:

```java
package com.gongwen.assistant.template;

import java.util.Optional;

public interface TemplateVersionRepository {
    TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath);

    int nextVersionNo(long templateId);

    Optional<TemplateVersion> findById(long id);

    void markParsed(long id, String profileHash);

    void markFailed(long id, String errorCode, String errorMessage);
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/JdbcTemplateVersionRepository.java`:

```java
package com.gongwen.assistant.template;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcTemplateVersionRepository implements TemplateVersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
        int versionNo = nextVersionNo(templateId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into document_template_version
                        (template_id, version_no, original_file_name, content_type, file_size_bytes, file_path, parse_status)
                    values
                        (?, ?, ?, ?, ?, ?, 'PENDING')
                    """, new String[]{"id"});
            statement.setLong(1, templateId);
            statement.setInt(2, versionNo);
            statement.setString(3, originalFileName);
            statement.setString(4, contentType);
            statement.setLong(5, fileSizeBytes);
            statement.setString(6, filePath);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public int nextVersionNo(long templateId) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "select coalesce(max(version_no), 0) from document_template_version where template_id = ?",
                Integer.class,
                templateId);
        return maxVersion + 1;
    }

    @Override
    public Optional<TemplateVersion> findById(long id) {
        return jdbcTemplate.query("select * from document_template_version where id = ?", this::mapRow, id)
                .stream()
                .findFirst();
    }

    @Override
    public void markParsed(long id, String profileHash) {
        jdbcTemplate.update("""
                update document_template_version
                set parse_status = 'READY',
                    profile_hash = ?,
                    parse_error_code = null,
                    parse_error_message = null
                where id = ?
                """, profileHash, id);
    }

    @Override
    public void markFailed(long id, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                update document_template_version
                set parse_status = 'FAILED',
                    parse_error_code = ?,
                    parse_error_message = ?
                where id = ?
                """, errorCode, errorMessage, id);
    }

    private TemplateVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TemplateVersion(
                rs.getLong("id"),
                rs.getLong("template_id"),
                rs.getInt("version_no"),
                rs.getString("original_file_name"),
                rs.getString("content_type"),
                rs.getLong("file_size_bytes"),
                rs.getString("file_path"),
                rs.getString("profile_hash"),
                rs.getString("parse_status"),
                rs.getString("parse_error_code"),
                rs.getString("parse_error_message"),
                rs.getObject("created_at", Instant.class)
        );
    }
}
```

- [ ] **Step 5: Enable configuration properties**

Modify `backend/src/main/java/com/gongwen/assistant/template/TemplateConfiguration.java`:

```java
package com.gongwen.assistant.template;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TemplateProperties.class)
public class TemplateConfiguration {
    @Bean
    DocxPlaceholderParser docxPlaceholderParser() {
        return new DocxPlaceholderParser();
    }
}
```

Modify `backend/src/main/resources/application.yml` by adding only the `template` section under the existing `gongwen` key:

```yaml
gongwen:
  template:
    storage-dir: ${GONGWEN_TEMPLATE_STORAGE_DIR:storage/templates}
    max-upload-mb: ${GONGWEN_TEMPLATE_MAX_UPLOAD_MB:20}
```

Keep existing `gongwen.material` and `gongwen.ai` settings unchanged.

Modify `.env.example`:

```env
GONGWEN_TEMPLATE_STORAGE_DIR=storage/templates
GONGWEN_TEMPLATE_MAX_UPLOAD_MB=20
```

- [ ] **Step 6: Run focused test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/TemplateProperties.java backend/src/main/java/com/gongwen/assistant/template/TemplateStorage.java backend/src/main/java/com/gongwen/assistant/template/LocalTemplateStorage.java backend/src/main/java/com/gongwen/assistant/template/TemplateVersion.java backend/src/main/java/com/gongwen/assistant/template/TemplateVersionRepository.java backend/src/main/java/com/gongwen/assistant/template/JdbcTemplateVersionRepository.java backend/src/main/java/com/gongwen/assistant/template/TemplateConfiguration.java backend/src/main/resources/application.yml .env.example backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java
git commit -m "feat: add template version storage"
```

---

### Task 3: Add Template Profile Model and Parser

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateStyleProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplatePlaceholderProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateSectionProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateTableProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateMediaProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateValidationItem.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`
- Create: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`

- [ ] **Step 1: Extend DocxTestFactory**

Modify `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java` by adding these methods before `readText`:

```java
public static byte[] docxWithSplitPlaceholder() {
    try (XWPFDocument document = new XWPFDocument();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("{{标");
        paragraph.createRun().setText("题}}");
        document.write(output);
        return output.toByteArray();
    } catch (IOException exception) {
        throw new IllegalStateException("Failed to create split placeholder docx", exception);
    }
}

public static byte[] docxWithOfficialStyles() {
    try (XWPFDocument document = new XWPFDocument();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        XWPFParagraph title = document.createParagraph();
        title.setStyle("official_title");
        title.createRun().setText("{{标题}}");

        XWPFParagraph body = document.createParagraph();
        body.setStyle("body_text");
        body.createRun().setText("{{正文}}");

        document.createHeader(org.apache.poi.xwpf.usermodel.HeaderFooterType.DEFAULT)
                .createParagraph()
                .createRun()
                .setText("机关公文");

        document.write(output);
        return output.toByteArray();
    } catch (IOException exception) {
        throw new IllegalStateException("Failed to create styled docx", exception);
    }
}
```

- [ ] **Step 2: Write failing parser tests**

Create `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`:

```java
package com.gongwen.assistant.template.profile;

import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateProfileParserTest {
    private final TemplateProfileParser parser = new TemplateProfileParser();

    @Test
    void parsesPlaceholdersAcrossRunsAndReportsRisk() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithSplitPlaceholder());

        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .containsExactly("标题");
        assertThat(profile.validationItems())
                .extracting(TemplateValidationItem::code)
                .contains("PLACEHOLDER_SPLIT_ACROSS_RUNS");
    }

    @Test
    void parsesStylesSectionsAndHeaderFooterSummary() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithOfficialStyles());

        assertThat(profile.styles())
                .extracting(TemplateStyleProfile::styleId)
                .contains("official_title", "body_text");
        assertThat(profile.sections()).hasSize(1);
        assertThat(profile.sections().getFirst().hasHeader()).isTrue();
        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .contains("标题", "正文");
    }

    @Test
    void parsesTablePlaceholders() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithTableCell("附件：{{附件}}"));

        assertThat(profile.tables()).hasSize(1);
        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .containsExactly("附件");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

Expected: FAIL because profile classes do not exist.

- [ ] **Step 4: Create profile records**

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfile.java`:

```java
package com.gongwen.assistant.template.profile;

import java.util.List;

public record TemplateProfile(
        int schemaVersion,
        List<TemplateStyleProfile> styles,
        List<TemplatePlaceholderProfile> placeholders,
        List<TemplateSectionProfile> sections,
        List<TemplateTableProfile> tables,
        List<TemplateMediaProfile> media,
        List<TemplateValidationItem> validationItems
) {
    public TemplateProfile {
        styles = List.copyOf(styles);
        placeholders = List.copyOf(placeholders);
        sections = List.copyOf(sections);
        tables = List.copyOf(tables);
        media = List.copyOf(media);
        validationItems = List.copyOf(validationItems);
    }
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateStyleProfile.java`:

```java
package com.gongwen.assistant.template.profile;

public record TemplateStyleProfile(
        String styleId,
        String styleName,
        String type,
        String basedOn,
        String fontFamily,
        Integer fontSizeHalfPoints,
        Boolean bold,
        String alignment,
        Integer indentationFirstLine,
        Integer spacingBetween,
        Integer spacingBefore,
        Integer spacingAfter
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplatePlaceholderProfile.java`:

```java
package com.gongwen.assistant.template.profile;

public record TemplatePlaceholderProfile(
        String key,
        String locationType,
        String paragraphKey,
        String styleId,
        String styleName,
        boolean splitAcrossRuns
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateSectionProfile.java`:

```java
package com.gongwen.assistant.template.profile;

public record TemplateSectionProfile(
        int index,
        boolean hasHeader,
        boolean hasFooter,
        Integer pageWidth,
        Integer pageHeight,
        Integer marginTop,
        Integer marginRight,
        Integer marginBottom,
        Integer marginLeft
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateTableProfile.java`:

```java
package com.gongwen.assistant.template.profile;

public record TemplateTableProfile(
        int index,
        int rowCount,
        int columnCount,
        int placeholderCount
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateMediaProfile.java`:

```java
package com.gongwen.assistant.template.profile;

public record TemplateMediaProfile(
        String mediaType,
        String relationshipId,
        String fileName
) {
}
```

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateValidationItem.java`:

```java
package com.gongwen.assistant.template.profile;

import java.util.Map;

public record TemplateValidationItem(
        String severity,
        String code,
        String message,
        String targetType,
        String targetKey,
        Map<String, Object> details
) {
    public TemplateValidationItem {
        details = Map.copyOf(details);
    }
}
```

- [ ] **Step 5: Implement TemplateProfileParser**

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`:

```java
package com.gongwen.assistant.template.profile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateProfileParser {
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public TemplateProfile parse(byte[] docxBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<TemplateValidationItem> validationItems = new ArrayList<>();
            List<TemplatePlaceholderProfile> placeholders = new ArrayList<>();
            collectParagraphPlaceholders(document.getParagraphs(), placeholders, validationItems, "PARAGRAPH");
            collectTableProfiles(document, placeholders, validationItems);
            return new TemplateProfile(
                    SCHEMA_VERSION,
                    parseStyles(document),
                    deduplicatePlaceholders(placeholders),
                    parseSections(document),
                    parseTables(document),
                    parseMedia(document),
                    validationItems
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Word 模板文件", exception);
        }
    }

    private List<TemplateStyleProfile> parseStyles(XWPFDocument document) {
        List<TemplateStyleProfile> profiles = new ArrayList<>();
        Set<String> styleIds = new LinkedHashSet<>();
        document.getParagraphs().forEach(paragraph -> {
            if (paragraph.getStyle() != null && !paragraph.getStyle().isBlank()) {
                styleIds.add(paragraph.getStyle());
            }
        });
        document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                .forEach(cell -> cell.getParagraphs().forEach(paragraph -> {
                    if (paragraph.getStyle() != null && !paragraph.getStyle().isBlank()) {
                        styleIds.add(paragraph.getStyle());
                    }
                }))));
        for (String styleId : styleIds) {
            profiles.add(new TemplateStyleProfile(
                    styleId,
                    document.getStyles() == null || document.getStyles().getStyle(styleId) == null
                            ? styleId
                            : document.getStyles().getStyle(styleId).getName(),
                    document.getStyles() == null || document.getStyles().getStyle(styleId) == null
                            ? "PARAGRAPH"
                            : String.valueOf(document.getStyles().getStyle(styleId).getType()),
                    document.getStyles() == null || document.getStyles().getStyle(styleId) == null
                            ? null
                            : document.getStyles().getStyle(styleId).getBasisStyleID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return profiles;
    }

    private void collectParagraphPlaceholders(
            List<XWPFParagraph> paragraphs,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateValidationItem> validationItems,
            String locationType
    ) {
        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String paragraphText = paragraph.getText();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(paragraphText == null ? "" : paragraphText);
            while (matcher.find()) {
                String key = matcher.group(1).trim();
                boolean splitAcrossRuns = isSplitAcrossRuns(paragraph, matcher.start(), matcher.end());
                String paragraphKey = locationType.toLowerCase() + "-" + index;
                placeholders.add(new TemplatePlaceholderProfile(
                        key,
                        locationType,
                        paragraphKey,
                        paragraph.getStyle(),
                        null,
                        splitAcrossRuns
                ));
                if (splitAcrossRuns) {
                    validationItems.add(new TemplateValidationItem(
                            "WARNING",
                            "PLACEHOLDER_SPLIT_ACROSS_RUNS",
                            "占位符跨多个 Word run，导出时需要结构级替换",
                            "PLACEHOLDER",
                            key,
                            Map.of("paragraphKey", paragraphKey)
                    ));
                }
            }
        }
    }

    private boolean isSplitAcrossRuns(XWPFParagraph paragraph, int start, int end) {
        int cursor = 0;
        int touchedRuns = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text == null) {
                continue;
            }
            int runStart = cursor;
            int runEnd = cursor + text.length();
            if (runEnd > start && runStart < end) {
                touchedRuns++;
            }
            cursor = runEnd;
        }
        return touchedRuns > 1;
    }

    private void collectTableProfiles(
            XWPFDocument document,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateValidationItem> validationItems
    ) {
        int tableIndex = 0;
        for (XWPFTable table : document.getTables()) {
            String paragraphPrefix = "table-" + tableIndex;
            int cellIndex = 0;
            for (var row : table.getRows()) {
                for (var cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        String text = paragraph.getText();
                        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text == null ? "" : text);
                        while (matcher.find()) {
                            String key = matcher.group(1).trim();
                            placeholders.add(new TemplatePlaceholderProfile(
                                    key,
                                    "TABLE",
                                    paragraphPrefix + "-cell-" + cellIndex,
                                    paragraph.getStyle(),
                                    null,
                                    isSplitAcrossRuns(paragraph, matcher.start(), matcher.end())
                            ));
                        }
                    }
                    cellIndex++;
                }
            }
            tableIndex++;
        }
    }

    private List<TemplatePlaceholderProfile> deduplicatePlaceholders(List<TemplatePlaceholderProfile> placeholders) {
        List<TemplatePlaceholderProfile> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TemplatePlaceholderProfile placeholder : placeholders) {
            if (seen.add(placeholder.key() + "|" + placeholder.locationType() + "|" + placeholder.paragraphKey())) {
                deduplicated.add(placeholder);
            }
        }
        return deduplicated;
    }

    private List<TemplateSectionProfile> parseSections(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().getSectPr();
        boolean hasHeader = !document.getHeaderList().isEmpty();
        boolean hasFooter = !document.getFooterList().isEmpty();
        if (section == null) {
            return List.of(new TemplateSectionProfile(0, hasHeader, hasFooter, null, null, null, null, null, null));
        }
        return List.of(new TemplateSectionProfile(
                0,
                hasHeader,
                hasFooter,
                section.isSetPgSz() ? intValue(section.getPgSz().getW()) : null,
                section.isSetPgSz() ? intValue(section.getPgSz().getH()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getTop()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getRight()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getBottom()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getLeft()) : null
        ));
    }

    private Integer intValue(Object value) {
        if (value instanceof BigInteger number) {
            return number.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private List<TemplateTableProfile> parseTables(XWPFDocument document) {
        List<TemplateTableProfile> profiles = new ArrayList<>();
        for (int index = 0; index < document.getTables().size(); index++) {
            XWPFTable table = document.getTables().get(index);
            int rowCount = table.getNumberOfRows();
            int columnCount = rowCount == 0 ? 0 : table.getRow(0).getTableCells().size();
            int placeholderCount = 0;
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(table.getText());
            while (matcher.find()) {
                placeholderCount++;
            }
            profiles.add(new TemplateTableProfile(index, rowCount, columnCount, placeholderCount));
        }
        return profiles;
    }

    private List<TemplateMediaProfile> parseMedia(XWPFDocument document) {
        return document.getAllPictures().stream()
                .map(picture -> new TemplateMediaProfile(
                        picture.suggestFileExtension(),
                        null,
                        picture.getFileName()
                ))
                .toList();
    }
}
```

- [ ] **Step 6: Register parser bean**

Modify `backend/src/main/java/com/gongwen/assistant/template/TemplateConfiguration.java`:

```java
import com.gongwen.assistant.template.profile.TemplateProfileParser;
```

Add:

```java
@Bean
TemplateProfileParser templateProfileParser() {
    return new TemplateProfileParser();
}
```

- [ ] **Step 7: Run parser tests**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/profile backend/src/main/java/com/gongwen/assistant/template/TemplateConfiguration.java backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java
git commit -m "feat: parse template profiles"
```

---

### Task 4: Persist Template Profiles and Validation Results

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/JdbcTemplateProfileRepository.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java`

- [ ] **Step 1: Add failing repository contract test through fake service**

Append to `TemplateUploadServiceTest`:

```java
@Test
void profileRepositoryStoresProfileForTemplateVersion() {
    InMemoryTemplateProfileRepository repository = new InMemoryTemplateProfileRepository();
    TemplateProfile profile = new TemplateProfile(
            1,
            List.of(),
            List.of(new TemplatePlaceholderProfile("标题", "PARAGRAPH", "paragraph-0", null, null, false)),
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
```

Add imports:

```java
import com.gongwen.assistant.template.profile.TemplatePlaceholderProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: FAIL because `TemplateProfileRepository` does not exist.

- [ ] **Step 3: Add repository interface**

Create `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileRepository.java`:

```java
package com.gongwen.assistant.template.profile;

import java.util.Optional;

public interface TemplateProfileRepository {
    void save(long templateVersionId, TemplateProfile profile, String profileHash);

    Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId);
}
```

- [ ] **Step 4: Add JDBC implementation**

Create `backend/src/main/java/com/gongwen/assistant/template/profile/JdbcTemplateProfileRepository.java`:

```java
package com.gongwen.assistant.template.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JdbcTemplateProfileRepository implements TemplateProfileRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTemplateProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
        jdbcTemplate.update("""
                insert into template_profile (template_version_id, schema_version, profile_json)
                values (?, ?, cast(? as jsonb))
                on conflict (template_version_id)
                do update set schema_version = excluded.schema_version,
                              profile_json = excluded.profile_json,
                              created_at = now()
                """, templateVersionId, profile.schemaVersion(), toJson(profile));
    }

    @Override
    public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
        return jdbcTemplate.query(
                        "select profile_json from template_profile where template_version_id = ?",
                        this::mapProfile,
                        templateVersionId)
                .stream()
                .findFirst();
    }

    private TemplateProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        Object value = rs.getObject("profile_json");
        String json = value instanceof PGobject pgObject ? pgObject.getValue() : String.valueOf(value);
        try {
            return objectMapper.readValue(json, TemplateProfile.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取模板 Profile", exception);
        }
    }

    private String toJson(TemplateProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化模板 Profile", exception);
        }
    }
}
```

- [ ] **Step 5: Run focused test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileRepository.java backend/src/main/java/com/gongwen/assistant/template/profile/JdbcTemplateProfileRepository.java backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java
git commit -m "feat: persist template profiles"
```

---

### Task 5: Add Template Upload Service

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadResponse.java`
- Create: `backend/src/main/java/com/gongwen/assistant/template/TemplateException.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java`

- [ ] **Step 1: Replace service test with upload orchestration tests**

Update `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java` to include:

```java
package com.gongwen.assistant.template;

import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateUploadServiceTest {
    @Test
    void uploadsTemplateVersionAndStoresProfile() throws IOException {
        Path storageDir = Files.createTempDirectory("template-upload-test");
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20)),
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties(storageDir.toString(), 20)
        );

        TemplateUploadResponse response = service.upload(
                1L,
                "通知模板.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocxTestFactory.docxWithOfficialStyles()
        );

        assertThat(response.templateVersionId()).isEqualTo(1L);
        assertThat(response.parseStatus()).isEqualTo("READY");
        assertThat(response.placeholderCount()).isEqualTo(2);
        assertThat(response.styleCount()).isGreaterThanOrEqualTo(2);
        assertThat(profileRepository.findByTemplateVersionId(1L)).isPresent();
        assertThat(versionRepository.findById(1L).orElseThrow().parseStatus()).isEqualTo("READY");
    }

    @Test
    void rejectsNonDocxTemplate() throws IOException {
        Path storageDir = Files.createTempDirectory("template-upload-test");
        TemplateUploadService service = new TemplateUploadService(
                new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20)),
                new InMemoryTemplateVersionRepository(),
                new InMemoryTemplateProfileRepository(),
                new TemplateProfileParser(),
                new TemplateProperties(storageDir.toString(), 20)
        );

        assertThatThrownBy(() -> service.upload(1L, "template.pdf", "application/pdf", "bad".getBytes()))
                .isInstanceOf(TemplateException.class)
                .hasMessageContaining("仅支持上传 Word 模板");
    }

    private static class InMemoryTemplateVersionRepository implements TemplateVersionRepository {
        private final Map<Long, TemplateVersion> versions = new HashMap<>();
        private long id = 1;

        @Override
        public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
            TemplateVersion version = new TemplateVersion(id++, templateId, nextVersionNo(templateId), originalFileName, contentType, fileSizeBytes, filePath, null, "PENDING", null, null, Instant.now());
            versions.put(version.id(), version);
            return version;
        }

        @Override
        public int nextVersionNo(long templateId) {
            return (int) versions.values().stream().filter(version -> version.templateId() == templateId).count() + 1;
        }

        @Override
        public Optional<TemplateVersion> findById(long id) {
            return Optional.ofNullable(versions.get(id));
        }

        @Override
        public void markParsed(long id, String profileHash) {
            TemplateVersion current = versions.get(id);
            versions.put(id, new TemplateVersion(current.id(), current.templateId(), current.versionNo(), current.originalFileName(), current.contentType(), current.fileSizeBytes(), current.filePath(), profileHash, "READY", null, null, current.createdAt()));
        }

        @Override
        public void markFailed(long id, String errorCode, String errorMessage) {
            TemplateVersion current = versions.get(id);
            versions.put(id, new TemplateVersion(current.id(), current.templateId(), current.versionNo(), current.originalFileName(), current.contentType(), current.fileSizeBytes(), current.filePath(), current.profileHash(), "FAILED", errorCode, errorMessage, current.createdAt()));
        }
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: FAIL because service and response classes do not exist.

- [ ] **Step 3: Add TemplateException**

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateException.java`:

```java
package com.gongwen.assistant.template;

public class TemplateException extends RuntimeException {
    private final String errorCode;

    public TemplateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TemplateException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 4: Add response record**

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadResponse.java`:

```java
package com.gongwen.assistant.template;

import java.util.List;

public record TemplateUploadResponse(
        long templateVersionId,
        int versionNo,
        String parseStatus,
        int placeholderCount,
        int styleCount,
        int validationCount,
        List<String> validationCodes
) {
    public TemplateUploadResponse {
        validationCodes = List.copyOf(validationCodes);
    }
}
```

- [ ] **Step 5: Add upload service**

Create `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java`:

```java
package com.gongwen.assistant.template;

import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class TemplateUploadService {
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TemplateStorage storage;
    private final TemplateVersionRepository versionRepository;
    private final TemplateProfileRepository profileRepository;
    private final TemplateProfileParser profileParser;
    private final TemplateProperties properties;

    public TemplateUploadService(
            TemplateStorage storage,
            TemplateVersionRepository versionRepository,
            TemplateProfileRepository profileRepository,
            TemplateProfileParser profileParser,
            TemplateProperties properties
    ) {
        this.storage = storage;
        this.versionRepository = versionRepository;
        this.profileRepository = profileRepository;
        this.profileParser = profileParser;
        this.properties = properties;
    }

    public TemplateUploadResponse upload(long templateId, String originalFileName, String contentType, byte[] content) {
        validate(originalFileName, contentType, content);
        TemplateVersion version = null;
        try {
            String filePath = storage.save(originalFileName, "docx", content);
            version = versionRepository.create(templateId, originalFileName, contentType, content.length, filePath);
            TemplateProfile profile = profileParser.parse(content);
            String profileHash = sha256(content);
            profileRepository.save(version.id(), profile, profileHash);
            versionRepository.markParsed(version.id(), profileHash);
            return new TemplateUploadResponse(
                    version.id(),
                    version.versionNo(),
                    "READY",
                    profile.placeholders().size(),
                    profile.styles().size(),
                    profile.validationItems().size(),
                    profile.validationItems().stream().map(item -> item.code()).toList()
            );
        } catch (IOException exception) {
            throw new TemplateException("TEMPLATE_STORAGE_FAILED", "模板文件保存失败", exception);
        } catch (RuntimeException exception) {
            if (version != null) {
                versionRepository.markFailed(version.id(), "TEMPLATE_PARSE_FAILED", exception.getMessage());
            }
            throw exception;
        }
    }

    private void validate(String originalFileName, String contentType, byte[] content) {
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".docx")) {
            throw new TemplateException("TEMPLATE_TYPE_NOT_ALLOWED", "仅支持上传 Word 模板 .docx");
        }
        if (!DOCX_CONTENT_TYPE.equals(contentType)) {
            throw new TemplateException("TEMPLATE_TYPE_NOT_ALLOWED", "仅支持上传 Word 模板 .docx");
        }
        long maxBytes = properties.maxUploadMb() * 1024L * 1024L;
        if (content.length > maxBytes) {
            throw new TemplateException("TEMPLATE_FILE_TOO_LARGE", "模板文件不能超过 " + properties.maxUploadMb() + "MB");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
```

- [ ] **Step 6: Run service test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java backend/src/main/java/com/gongwen/assistant/template/TemplateUploadResponse.java backend/src/main/java/com/gongwen/assistant/template/TemplateException.java backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java
git commit -m "feat: add template upload service"
```

---

### Task 6: Add Template Upload and Profile APIs

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/template/TemplateController.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/template/TemplateControllerTest.java`
- Create: `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadControllerTest.java`:

```java
package com.gongwen.assistant.template;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
class TemplateUploadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocxPlaceholderParser placeholderParser;

    @MockBean
    private TemplateUploadService uploadService;

    @MockBean
    private TemplateProfileRepository profileRepository;

    @Test
    void uploadsTemplateVersion() throws Exception {
        when(uploadService.upload(eq(1L), eq("notice.docx"), any(), any()))
                .thenReturn(new TemplateUploadResponse(9L, 1, "READY", 2, 4, 1, List.of("PLACEHOLDER_SPLIT_ACROSS_RUNS")));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notice.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/templates/1/versions").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateVersionId").value(9))
                .andExpect(jsonPath("$.data.parseStatus").value("READY"))
                .andExpect(jsonPath("$.data.validationCodes[0]").value("PLACEHOLDER_SPLIT_ACROSS_RUNS"));
    }

    @Test
    void returnsProfileByVersionId() throws Exception {
        when(profileRepository.findByTemplateVersionId(9L))
                .thenReturn(Optional.of(new TemplateProfile(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of())));

        mockMvc.perform(get("/api/templates/versions/9/profile").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schemaVersion").value(1));
    }

    @Test
    void normalizesTemplateErrors() throws Exception {
        when(uploadService.upload(eq(1L), eq("bad.pdf"), any(), any()))
                .thenThrow(new TemplateException("TEMPLATE_TYPE_NOT_ALLOWED", "仅支持上传 Word 模板 .docx"));
        MockMultipartFile file = new MockMultipartFile("file", "bad.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/templates/1/versions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TEMPLATE_TYPE_NOT_ALLOWED"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadControllerTest"
```

Expected: FAIL because endpoints do not exist.

- [ ] **Step 3: Modify TemplateController**

Replace `backend/src/main/java/com/gongwen/assistant/template/TemplateController.java` with:

```java
package com.gongwen.assistant.template;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final DocxPlaceholderParser parser;
    private final TemplateUploadService uploadService;
    private final TemplateProfileRepository profileRepository;

    public TemplateController(
            DocxPlaceholderParser parser,
            TemplateUploadService uploadService,
            TemplateProfileRepository profileRepository
    ) {
        this.parser = parser;
        this.uploadService = uploadService;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/parse")
    public ApiResponse<TemplateParseResponse> parse(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(new TemplateParseResponse(parser.parsePlaceholders(file.getBytes())));
    }

    @PostMapping("/{templateId}/versions")
    public ApiResponse<TemplateUploadResponse> uploadVersion(
            @PathVariable long templateId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(uploadService.upload(
                templateId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        ));
    }

    @GetMapping("/versions/{versionId}/profile")
    public ApiResponse<TemplateProfile> getProfile(@PathVariable long versionId) {
        return ApiResponse.ok(profileRepository.findByTemplateVersionId(versionId)
                .orElseThrow(() -> new TemplateException("TEMPLATE_PROFILE_NOT_FOUND", "模板 Profile 不存在")));
    }

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateException(TemplateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }

    public record TemplateParseResponse(List<String> placeholders) {
    }
}
```

- [ ] **Step 4: Run controller test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Update old template controller test for new constructor dependencies**

Modify `backend/src/test/java/com/gongwen/assistant/template/TemplateControllerTest.java` by adding imports:

```java
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.boot.test.mock.mockito.MockBean;
```

Add fields below `private MockMvc mockMvc;`:

```java
@MockBean
private TemplateUploadService uploadService;

@MockBean
private TemplateProfileRepository profileRepository;
```

- [ ] **Step 6: Run old template controller test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateControllerTest"
```

Expected: PASS. Existing `/api/templates/parse` behavior remains compatible.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/TemplateController.java backend/src/test/java/com/gongwen/assistant/template/TemplateUploadControllerTest.java backend/src/test/java/com/gongwen/assistant/template/TemplateControllerTest.java
git commit -m "feat: expose template profile APIs"
```

---

### Task 7: Update Project Documentation

**Files:**

- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`

- [ ] **Step 1: Update AGENTS template status**

In `AGENTS.md`, update current status and API list to include:

```markdown
模板引擎当前约定：

- 模板模块按 Word 样式体系优先设计，底层保存 `TemplateProfile`。
- 模板版本不可变，导出记录后续必须绑定具体模板版本。
- `TemplateProfile` 保存占位符、样式、section、表格、页眉页脚、媒体和解析风险摘要。
- 当前新增 T1 底座 API：`POST /api/templates/{templateId}/versions` 和 `GET /api/templates/versions/{versionId}/profile`。
- 首版模板后台仍未完成；当前 API 先服务后续 P8A/P10/P11。
```

- [ ] **Step 2: Update PROJECT_TASKS queue**

In `docs/PROJECT_TASKS.md`, add before P8:

```markdown
### P8A 模板引擎底座

状态：已完成 T1 后端基础。

目标：按 Word 样式体系优先建立模板版本、TemplateProfile、解析风险和上传解析 API。

已实现 API：

- `POST /api/templates/{templateId}/versions`
- `GET /api/templates/versions/{versionId}/profile`

后续依赖：

- P8B 模板驱动质检。
- P10 模板管理后台。
- P11 模板版本化导出体验。
```

- [ ] **Step 3: Run documentation grep**

Run:

```powershell
rg -n "P8A|TemplateProfile|/api/templates/.+versions|模板引擎" AGENTS.md docs/PROJECT_TASKS.md
```

Expected: matches in both docs.

- [ ] **Step 4: Commit**

```powershell
git add AGENTS.md docs/PROJECT_TASKS.md
git commit -m "docs: document template engine foundation"
```

---

### Task 8: Final Verification

**Files:**

- No file edits unless verification reveals a defect.

- [ ] **Step 1: Run parser focused test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

Expected: PASS.

- [ ] **Step 2: Run upload service focused test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
```

Expected: PASS.

- [ ] **Step 3: Run upload controller focused test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadControllerTest"
```

Expected: PASS.

- [ ] **Step 4: Run full backend test**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
```

Expected: PASS.

- [ ] **Step 5: Check git status**

Run:

```powershell
git status --short
```

Expected: only unrelated pre-existing worktree changes remain. No uncommitted files from this plan.

---

## Self-Review Notes

Spec coverage:

- Versioned upload: Tasks 1, 2, 5, 6.
- TemplateProfile JSONB: Tasks 1, 3, 4.
- Styles/placeholders/sections/tables/header-footer/media summaries: Task 3.
- Cross-run placeholder detection: Task 3.
- Template validation warnings: Tasks 3, 5, 6.
- API surface for later UI: Task 6.
- Documentation updates: Task 7.

Deferred by design:

- Export plan and renderer upgrade are T2.
- Template admin UI is T3.
- Template-driven quality checks are T4/P8B.
