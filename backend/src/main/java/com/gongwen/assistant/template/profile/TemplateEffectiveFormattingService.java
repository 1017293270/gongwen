package com.gongwen.assistant.template.profile;

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

    private TemplateStructureFormattingProfile resolveFirst(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> overrides,
            List<String> supportedStructureTypes
    ) {
        TemplateStructureProfile firstMatch = null;
        for (TemplateStructureProfile structure : profile.structures()) {
            if (!supportedStructureTypes.contains(structure.structureType())) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = structure;
            }
            if (overrides.containsKey(structure.structureKey())) {
                return merge(structure.formatting(), overrides.get(structure.structureKey()));
            }
        }
        if (firstMatch == null) {
            return null;
        }
        return merge(firstMatch.formatting(), overrides.get(firstMatch.structureKey()));
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
}
