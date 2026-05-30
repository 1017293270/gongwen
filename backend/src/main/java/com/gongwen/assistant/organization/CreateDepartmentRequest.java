package com.gongwen.assistant.organization;

public record CreateDepartmentRequest(
        Long parentId,
        String code,
        String name,
        Integer sortOrder
) {
}
