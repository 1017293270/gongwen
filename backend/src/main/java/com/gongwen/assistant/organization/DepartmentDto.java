package com.gongwen.assistant.organization;

import java.util.List;

public record DepartmentDto(
        long id,
        Long parentId,
        String code,
        String name,
        String status,
        int sortOrder,
        List<DepartmentDto> children
) {
    public DepartmentDto withoutChildren() {
        return new DepartmentDto(id, parentId, code, name, status, sortOrder, List.of());
    }
}
