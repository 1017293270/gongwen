package com.gongwen.assistant.documentstructure.semantic;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentHeadingRoleDetector;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class DocumentSemanticSuggester {
    private static final Pattern CHINESE_DATE_LINE_PATTERN = Pattern.compile("^\\d{4}年\\d{1,2}月\\d{1,2}日$");

    public DocumentStructureProfile suggest(DocumentStructureProfile profile, String documentKind, String documentTypeCode) {
        if (profile == null) {
            return new DocumentStructureProfile(
                    1,
                    "",
                    "document-structure-v2",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null
            );
        }
        DocumentSemanticContext context = new DocumentSemanticContext(documentKind, documentTypeCode);
        List<DocumentNode> nodes = new ArrayList<>();
        State state = new State();
        for (DocumentNode node : profile.nodes()) {
            String role = suggestRole(node, state, context);
            updateState(role, state);
            nodes.add(withRoleSuggestion(node, role));
        }
        return new DocumentStructureProfile(
                profile.schemaVersion(),
                profile.sourceFileHash(),
                profile.extractorVersion(),
                nodes,
                profile.styles(),
                profile.sections(),
                profile.risks(),
                profile.createdAt()
        );
    }

    private String suggestRole(DocumentNode node, State state, DocumentSemanticContext context) {
        String text = safeText(node.text()).strip();
        if (text.isBlank()) {
            return "UNKNOWN";
        }
        if ("HEADER_PARAGRAPH".equals(node.nodeType()) || "FOOTER_PARAGRAPH".equals(node.nodeType())) {
            return "STATIC_TEXT";
        }
        if ("TABLE_PARAGRAPH".equals(node.nodeType())) {
            return placeholderRole(text, "TABLE_ATTACHMENT");
        }
        String placeholderRole = placeholderRole(text, null);
        if (placeholderRole != null) {
            return placeholderRole;
        }
        if (isDateLine(text)) {
            return "DATE";
        }
        if (isAttachmentLine(text)) {
            state.seenBody = true;
            return "ATTACHMENT_NOTE";
        }
        if (!state.seenBody && !state.seenTitle && isLikelyIssuingOrgan(text)) {
            return "ISSUING_ORGAN";
        }
        if (!state.seenBody && !state.seenTitle && isLikelyDocNumber(text)) {
            return "DOC_NUMBER";
        }
        if (!state.seenTitle && isLikelyDocumentTitle(node, text, context)) {
            return "TITLE";
        }
        if (isLikelyRecipient(text, state)) {
            return "RECIPIENT";
        }
        String headingRole = DocumentHeadingRoleDetector.detect(text);
        if (!headingRole.isBlank()) {
            state.seenBody = true;
            return headingRole;
        }
        if (isLikelySignature(text, state)) {
            return "SIGNATURE";
        }
        if (isLikelyBody(text, state)) {
            state.seenBody = true;
            return "BODY";
        }
        return "UNKNOWN";
    }

    private void updateState(String role, State state) {
        if ("TITLE".equals(role)) {
            state.seenTitle = true;
        }
        if ("RECIPIENT".equals(role)) {
            state.seenRecipient = true;
        }
        if ("BODY".equals(role) || role.startsWith("BODY_HEADING")) {
            state.seenBody = true;
        }
    }

    private String placeholderRole(String text, String fallbackForTable) {
        String normalized = text.toLowerCase();
        if (containsAny(normalized, "{{标题", "{{title")) {
            return "TITLE";
        }
        if (containsAny(normalized, "{{主送", "{{recipient")) {
            return "RECIPIENT";
        }
        if (containsAny(normalized, "{{正文", "{{body")) {
            return "BODY";
        }
        if (containsAny(normalized, "{{附件", "{{attachment")) {
            return "ATTACHMENT_NOTE";
        }
        if (containsAny(normalized, "{{落款", "{{signature")) {
            return "SIGNATURE";
        }
        if (containsAny(normalized, "{{日期", "{{date")) {
            return "DATE";
        }
        return fallbackForTable;
    }

    private boolean isLikelyDocumentTitle(DocumentNode node, String text, DocumentSemanticContext context) {
        if (text.length() > 80 || text.endsWith("：") || text.endsWith(":") || isDateLine(text)) {
            return false;
        }
        String alignment = node.formatting() == null ? "" : safeText(node.formatting().alignment());
        boolean centered = "CENTER".equalsIgnoreCase(alignment);
        boolean officialKeyword = containsAny(text, "通知", "请示", "报告", "讲话", "发言", "会议", "推进会", "方案", "意见", "函");
        boolean targetKindHint = !"UNKNOWN".equals(context.documentTypeCode()) && officialKeyword;
        return centered || officialKeyword || targetKindHint;
    }

    private boolean isLikelyRecipient(String text, State state) {
        return state.seenTitle
                && !state.seenBody
                && !state.seenRecipient
                && text.length() <= 80
                && (text.endsWith("：") || text.endsWith(":"));
    }

    private boolean isLikelyBody(String text, State state) {
        if (!state.seenTitle || text.length() < 8) {
            return false;
        }
        return state.seenRecipient || state.seenBody || text.length() > 40;
    }

    private boolean isLikelyIssuingOrgan(String text) {
        return text.length() <= 40 && containsAny(text, "文件", "委员会", "办公室");
    }

    private boolean isLikelyDocNumber(String text) {
        return text.length() <= 40 && text.contains("〔") && text.contains("〕") && text.endsWith("号");
    }

    private boolean isLikelySignature(String text, State state) {
        return state.seenBody
                && text.length() <= 40
                && !text.endsWith("。")
                && containsAny(text, "委员会", "办公室", "公司", "局", "处");
    }

    private boolean isDateLine(String text) {
        return CHINESE_DATE_LINE_PATTERN.matcher(text).matches();
    }

    private boolean isAttachmentLine(String text) {
        return text.startsWith("附件：") || text.startsWith("附件:");
    }

    private boolean containsAny(String raw, String... values) {
        for (String value : values) {
            if (raw.contains(value.toLowerCase()) || raw.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private DocumentNode withRoleSuggestion(DocumentNode node, String roleSuggestion) {
        return new DocumentNode(
                node.nodeKey(),
                node.parentKey(),
                node.nodeType(),
                roleSuggestion,
                node.text(),
                node.textPreview(),
                node.orderIndex(),
                node.path(),
                node.formatting(),
                node.riskCodes(),
                node.location(),
                node.runs(),
                node.numbering()
        );
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static class State {
        private boolean seenTitle;
        private boolean seenRecipient;
        private boolean seenBody;
    }
}
