# DOCX Fixture Coverage

This folder records the fixture categories required by P10D T16. Most fixture
documents are generated in-memory by `DocxTestFactory` so focused tests stay
small and deterministic.

| Category | Factory coverage | Focused tests |
| --- | --- | --- |
| Placeholder template | `docxWithOfficialStyles`, `docxWithSplitPlaceholder` | `TemplateUploadServiceTest`, `TemplateProfileParserTest` |
| Style template | `docxWithParagraphs`, `docxWithNoticeReferenceSkeleton` | `TemplateUploadServiceTest`, `TemplateProfileParserTest` |
| Reference official document | `docxWithNoticeReferenceSkeleton`, `docxWithNoticeReferenceFormatting`, `speechReferenceDocument` | `TemplateProfileParserTest`, `DocumentStructureExtractorTest`, `DocxTemplateRendererTest`, `DocxCompleteStructurePipelineTest`, `DocxNodeReplacementRendererTest` |
| Manual or guide | `docxWithManualGuideLikeDocument` | `TemplateUploadServiceTest`, `StructureMappingServiceTest`, `QualityCheckServiceTest`, `DraftWordExportServiceTest` |
| Complex table | `docxWithComplexTable` | `TemplateProfileParserTest` |
| Header and footer | `docxWithHeaderAndFooter`, `docxWithNoticeReferenceSkeleton`, `speechReferenceDocument` | `TemplateProfileParserTest`, `DocxTemplateRendererTest`, `DocxCompleteStructurePipelineTest`, `DocxNodeReplacementRendererTest` |
| Missing font | `docxWithMissingFontFormatting` | `TemplateProfileParserTest` |

T25 adds a lightweight pipeline fixture around `speechReferenceDocument`:
upload -> fact extraction -> semantic suggestions -> confirmed mapping ->
DraftNode initialization -> original DOCX node replacement export. This keeps
the no-placeholder reference-document path covered without requiring
LibreOffice in automated tests.
