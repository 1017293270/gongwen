package com.gongwen.assistant.organization;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository {
    List<DepartmentDto> findAll();

    DepartmentDto create(Long parentId, String code, String name, int sortOrder);

    Optional<DepartmentDto> update(long id, Long parentId, String name, String status, int sortOrder);

    int countChildren(long id);

    int countUsers(long id);

    int countBusinessReferences(long id);

    boolean delete(long id);
}
