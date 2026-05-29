package com.gongwen.assistant.template.profile;

import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateEffectiveFormattingServiceTest {
    private final TemplateEffectiveFormattingService service = new TemplateEffectiveFormattingService();

    @Test
    void resolvesBodyFormattingUsingOverrideOverProfileDefault() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("body-1", "BODY", formatting(null, 32, false, "LEFT", 420, 360, 0, 0)),
                structure("signature-1", "SIGNATURE", formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0))
        );
        Map<String, TemplateStructureFormattingProfile> overrides = Map.of(
                "body-1", formatting("KaiTi", 32, null, null, 560, 420, null, null)
        );

        ExportFormattingContext context = service.resolve(profile, overrides);

        assertThat(context.body()).isNotNull();
        assertThat(context.body().fontFamily()).isEqualTo("KaiTi");
        assertThat(context.body().indentationFirstLine()).isEqualTo(560);
        assertThat(context.body().spacingBetween()).isEqualTo(420);
        assertThat(context.body().alignment()).isEqualTo("LEFT");
        assertThat(context.signature().alignment()).isEqualTo("RIGHT");
    }

    @Test
    void fallsBackToProfileFormattingWhenNoOverrideExists() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("title-1", "TITLE", formatting("FangSong", 44, true, "CENTER", 0, 0, 0, 240))
        );

        ExportFormattingContext context = service.resolve(profile, Map.of());

        assertThat(context.title()).isNotNull();
        assertThat(context.title().alignment()).isEqualTo("CENTER");
        assertThat(context.title().fontSizeHalfPoints()).isEqualTo(44);
        assertThat(context.body()).isNull();
    }

    @Test
    void resolvesOnlySupportedSemanticSlots() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("recipient-1", "RECIPIENT", formatting("FangSong", 32, false, "LEFT", 0, 0, 0, 0)),
                structure("footer-1", "FOOTER", formatting("FangSong", 24, false, "CENTER", 0, 0, 0, 0)),
                structure("date-1", "DATE", formatting("FangSong", 28, false, "RIGHT", 0, 0, 0, 0))
        );

        ExportFormattingContext context = service.resolve(profile, Map.of());

        assertThat(context.recipient()).isNotNull();
        assertThat(context.recipient().alignment()).isEqualTo("LEFT");
        assertThat(context.date()).isNotNull();
        assertThat(context.date().alignment()).isEqualTo("RIGHT");
        assertThat(context.title()).isNull();
        assertThat(context.body()).isNull();
        assertThat(context.signature()).isNull();
    }

    @Test
    void resolvesMatchingStructureDeterministicallyByPreferringOverrideThenDocumentOrder() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("body-2", "BODY", formatting("FangSong", 32, false, "BOTH", 420, 360, 0, 0)),
                structure("body-1", "BODY", formatting("KaiTi", 30, false, "LEFT", 280, 300, 0, 0)),
                structure("body-3", "BODY", formatting("SongTi", 34, false, "CENTER", 560, 420, 0, 0))
        );

        ExportFormattingContext withoutOverrides = service.resolve(profile, Map.of());

        assertThat(withoutOverrides.body()).isNotNull();
        assertThat(withoutOverrides.body().fontFamily()).isEqualTo("FangSong");
        assertThat(withoutOverrides.body().fontSizeHalfPoints()).isEqualTo(32);

        ExportFormattingContext withOverride = service.resolve(profile, Map.of(
                "body-3", formatting("HeiTi", null, null, null, null, null, null, null)
        ));

        assertThat(withOverride.body()).isNotNull();
        assertThat(withOverride.body().fontFamily()).isEqualTo("HeiTi");
        assertThat(withOverride.body().fontSizeHalfPoints()).isEqualTo(34);
        assertThat(withOverride.body().alignment()).isEqualTo("CENTER");
    }

    @Test
    void keepsParsedBodyOrderWhenStructureKeysStopSortingLikeDocumentOrder() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("paragraph-2", "BODY", formatting("KaiTi", 32, false, "LEFT", 420, 150, 0, 0)),
                structure("paragraph-10", "BODY", formatting("FangSong", 30, false, "BOTH", 280, 200, 0, 0))
        );

        ExportFormattingContext context = service.resolve(profile, Map.of());

        assertThat(context.body()).isNotNull();
        assertThat(context.body().fontFamily()).isEqualTo("KaiTi");
        assertThat(context.body().spacingBetween()).isEqualTo(150);
    }

    @Test
    void ignoresBlankStringOverridesWhenMergingFormatting() {
        TemplateProfile profile = templateProfileWithStructures(
                structure("title-1", "TITLE", formatting("FangSong", 44, true, "CENTER", 0, 0, 0, 240))
        );

        ExportFormattingContext context = service.resolve(profile, Map.of(
                "title-1", formatting("   ", null, null, "", null, null, null, null)
        ));

        assertThat(context.title()).isNotNull();
        assertThat(context.title().fontFamily()).isEqualTo("FangSong");
        assertThat(context.title().alignment()).isEqualTo("CENTER");
    }

    @Test
    void returnsEmptyFormattingContextWhenProfileAndOverridesAreNull() {
        ExportFormattingContext context = service.resolve(null, null);

        assertThat(context).isSameAs(ExportFormattingContext.EMPTY);
        assertThat(context.title()).isNull();
        assertThat(context.recipient()).isNull();
        assertThat(context.body()).isNull();
        assertThat(context.signature()).isNull();
        assertThat(context.date()).isNull();
    }

    private static TemplateProfile templateProfileWithStructures(TemplateStructureProfile... structures) {
        return new TemplateProfile(
                1,
                List.of(structures),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static TemplateStructureProfile structure(
            String structureKey,
            String structureType,
            TemplateStructureFormattingProfile formatting
    ) {
        return new TemplateStructureProfile(
                structureKey,
                structureType,
                structureType,
                structureKey,
                "BODY",
                null,
                null,
                "PROFILE",
                formatting
        );
    }

    private static TemplateStructureFormattingProfile formatting(
            String fontFamily,
            Integer fontSizeHalfPoints,
            Boolean bold,
            String alignment,
            Integer indentationFirstLine,
            Integer spacingBetween,
            Integer spacingBefore,
            Integer spacingAfter
    ) {
        return new TemplateStructureFormattingProfile(
                fontFamily,
                fontSizeHalfPoints,
                bold,
                alignment,
                indentationFirstLine,
                spacingBetween,
                spacingBefore,
                spacingAfter
        );
    }
}
