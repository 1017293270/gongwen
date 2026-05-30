package com.gongwen.assistant.organization;

public record UpdateDepartmentRequest(
        Long parentId,
        String name,
        String status,
        Integer sortOrder
) {
}
