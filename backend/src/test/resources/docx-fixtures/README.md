# DOCX Fixture Coverage

This folder records the fixture categories required by P10D T16. Most fixture
documents are generated in-memory by `DocxTestFactory` so focused tests stay
small and deterministic.

| Category | Factory coverage | Focused tests |
| --- | --- | --- |
| Placeholder template | `docxWithOfficialStyles`, `docxWithSplitPlaceholder` | `TemplateUploadServiceTest`, `TemplateProfileParserTest` |
| Style template | `docxWithParagraphs`, `docxWithNoticeReferenceSkeleton` | `TemplateUploadServiceTest`, `TemplateProfileParserTest` |
| Reference official document | `docxWithNoticeReferenceSkeleton`, `docxWithNoticeReferenceFormatting` | `TemplateProfileParserTest`, `DocumentStructureExtractorTest`, `DocxTemplateRendererTest` |
| Manual or guide | `docxWithManualGuideLikeDocument` | `TemplateUploadServiceTest`, `StructureMappingServiceTest`, `QualityCheckServiceTest`, `DraftWordExportServiceTest` |
| Complex table | `docxWithComplexTable` | `TemplateProfileParserTest` |
| Header and footer | `docxWithHeaderAndFooter`, `docxWithNoticeReferenceSkeleton` | `TemplateProfileParserTest`, `DocxTemplateRendererTest` |
| Missing font | `docxWithMissingFontFormatting` | `TemplateProfileParserTest` |
