package com.gongwen.assistant.template;

import com.gongwen.assistant.ai.ModelAdapter;
import com.gongwen.assistant.ai.MockModelAdapter;
import com.gongwen.assistant.ai.TemplateAnalysisPrompt;
import com.gongwen.assistant.ai.TemplateAnalysisResponse;
import com.gongwen.assistant.template.profile.TemplateAnalysisProfile;
import com.gongwen.assistant.template.profile.TemplatePlaceholderSuggestionProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Service
public class TemplateIntelligenceService {
    private static final int TEXT_SAMPLE_LIMIT = 2400;

    private final ModelAdapter modelAdapter;

    public TemplateIntelligenceService(ModelAdapter modelAdapter) {
        this.modelAdapter = modelAdapter;
    }

    public TemplateProfile enrich(String documentTypeCode, String originalFileName, byte[] content, TemplateProfile profile) {
        if (!profile.placeholders().isEmpty()) {
            return profile.withTemplateAnalysis(new TemplateAnalysisProfile(
                    "STANDARD_PLACEHOLDER_TEMPLATE",
                    1.0,
                    documentTypeCode,
                    profile.placeholders().stream().map(placeholder -> placeholder.key()).distinct().toList(),
                    List.of(),
                    "已识别到显式占位符，可直接用于自动套版。",
                    "RULE"
            ));
        }

        TemplateAnalysisPrompt prompt = new TemplateAnalysisPrompt(
                documentTypeCode,
                originalFileName,
                extractTextSample(content),
                profile.styles().stream().map(style -> style.styleName() == null ? style.styleId() : style.styleName()).toList(),
                profile.tables().size(),
                profile.sections().stream().anyMatch(section -> section.hasHeader()),
                profile.sections().stream().anyMatch(section -> section.hasFooter())
        );

        try {
            return profile.withTemplateAnalysis(toProfile(modelAdapter.generateTemplateAnalysis(prompt)));
        } catch (RuntimeException exception) {
            return profile.withTemplateAnalysis(toProfile(new MockModelAdapter().generateTemplateAnalysis(prompt)));
        }
    }

    private TemplateAnalysisProfile toProfile(TemplateAnalysisResponse response) {
        return new TemplateAnalysisProfile(
                response.templateKind(),
                response.confidence(),
                response.documentTypeCode(),
                response.inferredFields(),
                response.suggestedPlaceholders().stream()
                        .map(suggestion -> new TemplatePlaceholderSuggestionProfile(suggestion.field(), suggestion.reason()))
                        .toList(),
                response.message(),
                response.source()
        );
    }

    private String extractTextSample(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> appendSample(text, paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                    .forEach(cell -> appendSample(text, cell.getText()))));
            return text.length() > TEXT_SAMPLE_LIMIT ? text.substring(0, TEXT_SAMPLE_LIMIT) : text.toString();
        } catch (IOException exception) {
            return "";
        }
    }

    private void appendSample(StringBuilder text, String value) {
        if (value == null || value.isBlank() || text.length() >= TEXT_SAMPLE_LIMIT) {
            return;
        }
        text.append(value.strip()).append('\n');
    }
}
