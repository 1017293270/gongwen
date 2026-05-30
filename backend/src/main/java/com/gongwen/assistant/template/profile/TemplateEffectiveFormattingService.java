package com.gongwen.assistant.template.profile;

import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Resolves the export-facing formatting contract from parsed template structures and saved overrides.
 * <p>
 * When multiple structures share the same semantic slot, resolution is deterministic:
 * prefer the first structure with a saved override in document order, otherwise
 * keep the first parsed structure. This keeps export and quality aligned with the
 * template's natural reading order while still favoring the structure an
 * administrator explicitly customized.
 */
@Service
public final class TemplateEffectiveFormattingService {
    private static final List<String> TITLE_TYPES = List.of("TITLE");
    private static final List<String> RECIPIENT_TYPES = List.of("RECIPIENT");
    private static final List<String> BODY_TYPES = List.of("BODY");
    private static final List<String> SIGNATURE_TYPES = List.of("SIGNATURE");
    private static final List<String> DATE_TYPES = List.of("DATE");
    private static final String BODY_TYPE = "BODY";

    public ExportFormattingContext resolve(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> overrides
    ) {
        if (profile == null) {
            return ExportFormattingContext.EMPTY;
        }
        Map<String, TemplateStructureFormattingProfile> safeOverrides = overrides == null ? Map.of() : overrides;
        return new ExportFormattingContext(
                resolveFirst(profile, safeOverrides, TITLE_TYPES),
                resolveFirst(profile, safeOverrides, RECIPIENT_TYPES),
                resolveFirst(profile, safeOverrides, BODY_TYPES),
                resolveFirst(profile, safeOverrides, SIGNATURE_TYPES),
                resolveFirst(profile, safeOverrides, DATE_TYPES)
        );
    }

    public TemplateStructureFormattingProfile resolveDraftNodeFormatting(
            TemplateStructureFormattingProfile systemDefault,
            TemplateStructureFormattingProfile documentTypeDefault,
            TemplateStructureFormattingProfile originalEffectiveFormatting,
            TemplateStructureFormattingProfile structureMappingOverride,
            DraftNodeFormatOverride draftNodeOverride
    ) {
        TemplateStructureFormattingProfile resolved = systemDefault;
        resolved = mergeNullable(resolved, documentTypeDefault);
        resolved = mergeNullable(resolved, originalEffectiveFormatting);
        resolved = mergeNullable(resolved, structureMappingOverride);
        return mergeNullable(resolved, draftNodeOverrideToFormatting(draftNodeOverride));
    }

    private TemplateStructureFormattingProfile resolveFirst(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> overrides,
            List<String> supportedStructureTypes
    ) {
        TemplateStructureProfile firstMatch = null;
        TemplateStructureProfile preferredDefaultMatch = null;
        for (TemplateStructureProfile structure : profile.structures()) {
            if (!supportedStructureTypes.contains(structure.structureType())) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = structure;
            }
            if (preferredDefaultMatch == null && isPreferredDefaultMatch(structure, supportedStructureTypes)) {
                preferredDefaultMatch = structure;
            }
            if (overrides.containsKey(structure.structureKey())) {
                return merge(structure.formatting(), overrides.get(structure.structureKey()));
            }
        }
        if (firstMatch == null) {
            return null;
        }
        TemplateStructureProfile selected = preferredDefaultMatch == null ? firstMatch : preferredDefaultMatch;
        return merge(selected.formatting(), overrides.get(selected.structureKey()));
    }

    private boolean isPreferredDefaultMatch(
            TemplateStructureProfile structure,
            List<String> supportedStructureTypes
    ) {
        if (!supportedStructureTypes.contains(BODY_TYPE)) {
            return false;
        }
        TemplateStructureFormattingProfile formatting = structure.formatting();
        return formatting != null
                && formatting.indentationFirstLine() != null
                && formatting.indentationFirstLine() > 0;
    }

    private TemplateStructureFormattingProfile merge(
            TemplateStructureFormattingProfile profileFormatting,
            TemplateStructureFormattingProfile overrideFormatting
    ) {
        if (profileFormatting == null) {
            return overrideFormatting;
        }
        return profileFormatting.mergeOverride(overrideFormatting);
    }

    private TemplateStructureFormattingProfile mergeNullable(
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile overrideFormatting
    ) {
        if (baseFormatting == null) {
            return overrideFormatting;
        }
        return baseFormatting.mergeOverride(overrideFormatting);
    }

    private TemplateStructureFormattingProfile draftNodeOverrideToFormatting(DraftNodeFormatOverride override) {
        if (override == null || override.isEmpty()) {
            return null;
        }
        String fontFamily = override.eastAsiaFont() == null ? override.latinFont() : override.eastAsiaFont();
        Integer fontSizeHalfPoints = override.fontSizePt() == null
                ? null
                : (int) Math.round(override.fontSizePt() * 2);
        TemplateLineSpacingProfile lineSpacing = lineSpacingProfile(override.lineSpacingRule(), override.lineSpacingTwip());
        Integer spacingBetween = "AUTO".equals(override.lineSpacingRule()) ? override.lineSpacingTwip() : null;
        return new TemplateStructureFormattingProfile(
                fontFamily,
                fontSizeHalfPoints,
                override.bold(),
                override.alignment(),
                override.firstLineIndentTwip(),
                spacingBetween,
                override.spacingBeforeTwip(),
                override.spacingAfterTwip(),
                null,
                override.eastAsiaFont(),
                override.latinFont(),
                lineSpacing
        );
    }

    private TemplateLineSpacingProfile lineSpacingProfile(String rule, Integer value) {
        if (rule == null && value == null) {
            return null;
        }
        String mode = rule == null ? "AUTO" : rule;
        if ("AUTO".equals(mode)) {
            return new TemplateLineSpacingProfile("AUTO", null, value);
        }
        return new TemplateLineSpacingProfile(mode, value, null);
    }
}
