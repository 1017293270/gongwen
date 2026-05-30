package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;

/**
 * Compatibility wrapper for the P10D document-structure contract.
 * The first pass reuses the existing template formatting DTO directly.
 */
public record DocumentNodeFormatting(
        TemplateStructureFormattingProfile effectiveFormatting
) {
}
