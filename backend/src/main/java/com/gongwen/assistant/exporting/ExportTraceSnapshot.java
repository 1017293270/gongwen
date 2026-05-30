package com.gongwen.assistant.exporting;

public record ExportTraceSnapshot(
        Long structureMappingProfileId,
        Integer structureMappingVersion,
        Object structureProfileSnapshot,
        Object mappingProfileSnapshot,
        Object formattingSnapshot,
        Object nodeSnapshot
) {
    public static final ExportTraceSnapshot EMPTY =
            new ExportTraceSnapshot(null, null, null, null, null, null);
}
