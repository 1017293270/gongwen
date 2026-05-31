package com.gongwen.assistant.exporting;

public record ExportTraceSnapshot(
        Long structureMappingProfileId,
        Integer structureMappingVersion,
        Object structureProfileSnapshot,
        Object mappingProfileSnapshot,
        Object formattingSnapshot,
        Object nodeSnapshot,
        String strategy
) {
    public static final ExportTraceSnapshot EMPTY =
            new ExportTraceSnapshot(null, null, null, null, null, null, null);

    public ExportTraceSnapshot(
            Long structureMappingProfileId,
            Integer structureMappingVersion,
            Object structureProfileSnapshot,
            Object mappingProfileSnapshot,
            Object formattingSnapshot,
            Object nodeSnapshot
    ) {
        this(
                structureMappingProfileId,
                structureMappingVersion,
                structureProfileSnapshot,
                mappingProfileSnapshot,
                formattingSnapshot,
                nodeSnapshot,
                null
        );
    }

    public ExportTraceSnapshot withStrategy(String nextStrategy) {
        return new ExportTraceSnapshot(
                structureMappingProfileId,
                structureMappingVersion,
                structureProfileSnapshot,
                mappingProfileSnapshot,
                formattingSnapshot,
                nodeSnapshot,
                nextStrategy
        );
    }
}
