package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;

public record ExportFormattingContext(
        TemplateStructureFormattingProfile title,
        TemplateStructureFormattingProfile recipient,
        TemplateStructureFormattingProfile body,
        TemplateStructureFormattingProfile signature,
        TemplateStructureFormattingProfile date
) {
    public static final ExportFormattingContext EMPTY =
            new ExportFormattingContext(null, null, null, null, null);
}
