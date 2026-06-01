package com.gongwen.assistant.organization;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class DepartmentService {
    private static final Pattern HIERARCHICAL_CODE_PATTERN = Pattern.compile("(?:A\\d{2,})+");
    private static final Pattern CODE_SEGMENT_PATTERN = Pattern.compile("A\\d{2,}");

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<DepartmentDto> tree() {
        List<DepartmentDto> flat = departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(DepartmentDto::sortOrder).thenComparing(DepartmentDto::id))
                .toList();
        Map<Long, MutableDepartmentNode> nodes = new LinkedHashMap<>();
        for (DepartmentDto department : flat) {
            nodes.put(department.id(), new MutableDepartmentNode(department));
        }
        List<MutableDepartmentNode> roots = new ArrayList<>();
        for (MutableDepartmentNode node : nodes.values()) {
            Long parentId = node.department.parentId();
            if (parentId != null && nodes.containsKey(parentId)) {
                nodes.get(parentId).children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots.stream().map(MutableDepartmentNode::toDto).toList();
    }

    public DepartmentDto create(CreateDepartmentRequest request) {
        Long parentId = request == null ? null : request.parentId();
        String code = generateDepartmentCode(parentId);
        String name = normalizeName(request == null ? null : request.name());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        return departmentRepository.create(parentId, code, name, sortOrder);
    }

    public DepartmentDto update(long id, UpdateDepartmentRequest request) {
        String name = normalizeName(request == null ? null : request.name());
        String status = normalizeStatus(request == null ? null : request.status());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        return departmentRepository.update(id, request == null ? null : request.parentId(), name, status, sortOrder)
                .orElseThrow(() -> new DepartmentException("DEPARTMENT_NOT_FOUND", "Department not found"));
    }

    public void delete(long id) {
        if (departmentRepository.countChildren(id) > 0) {
            throw new DepartmentException("DEPARTMENT_HAS_CHILDREN", "Department has child departments and cannot be deleted");
        }
        if (departmentRepository.countUsers(id) > 0) {
            throw new DepartmentException("DEPARTMENT_HAS_USERS", "Department has users and cannot be deleted");
        }
        if (departmentRepository.countBusinessReferences(id) > 0) {
            throw new DepartmentException("DEPARTMENT_HAS_BUSINESS_DATA", "Department has business data and cannot be deleted");
        }
        if (!departmentRepository.delete(id)) {
            throw new DepartmentException("DEPARTMENT_NOT_FOUND", "Department not found");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new DepartmentException("DEPARTMENT_NAME_REQUIRED", "Department name is required");
        }
        return name.strip();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.strip().toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) {
            throw new DepartmentException("DEPARTMENT_STATUS_INVALID", "Department status is invalid");
        }
        return normalized;
    }

    private String generateDepartmentCode(Long parentId) {
        List<DepartmentDto> departments = departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(DepartmentDto::sortOrder).thenComparing(DepartmentDto::id))
                .toList();
        String parentPrefix = parentId == null ? "" : departmentCodePrefix(departments, parentId);
        List<DepartmentDto> siblings = departments.stream()
                .filter(department -> Objects.equals(department.parentId(), parentId))
                .toList();
        int nextIndex = nextSegmentIndex(parentPrefix, siblings);
        return parentPrefix + formatSegment(nextIndex);
    }

    private String departmentCodePrefix(List<DepartmentDto> departments, long parentId) {
        DepartmentDto parent = departments.stream()
                .filter(department -> department.id() == parentId)
                .findFirst()
                .orElseThrow(() -> new DepartmentException("DEPARTMENT_PARENT_NOT_FOUND", "Parent department not found"));
        if (HIERARCHICAL_CODE_PATTERN.matcher(parent.code()).matches()) {
            return parent.code();
        }
        return deriveDepartmentCodeFromPath(departments, parentId);
    }

    private String deriveDepartmentCodeFromPath(List<DepartmentDto> departments, long targetId) {
        Map<Long, List<DepartmentDto>> childrenByParent = groupDepartmentsByParent(departments);
        List<DepartmentDto> path = findDepartmentPath(childrenByParent, null, targetId);
        if (path.isEmpty()) {
            throw new DepartmentException("DEPARTMENT_PARENT_NOT_FOUND", "Parent department not found");
        }
        List<String> segments = new ArrayList<>();
        Long currentParentId = null;
        for (DepartmentDto department : path) {
            List<DepartmentDto> siblings = childrenByParent.getOrDefault(currentParentId, List.of());
            int index = 1;
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).id() == department.id()) {
                    index = i + 1;
                    break;
                }
            }
            segments.add(formatSegment(index));
            currentParentId = department.id();
        }
        return String.join("", segments);
    }

    private Map<Long, List<DepartmentDto>> groupDepartmentsByParent(List<DepartmentDto> departments) {
        Map<Long, List<DepartmentDto>> childrenByParent = new LinkedHashMap<>();
        for (DepartmentDto department : departments) {
            childrenByParent.computeIfAbsent(department.parentId(), ignored -> new ArrayList<>()).add(department);
        }
        childrenByParent.values().forEach(children -> children.sort(
                Comparator.comparing(DepartmentDto::sortOrder).thenComparing(DepartmentDto::id)));
        return childrenByParent;
    }

    private List<DepartmentDto> findDepartmentPath(Map<Long, List<DepartmentDto>> childrenByParent, Long parentId, long targetId) {
        for (DepartmentDto department : childrenByParent.getOrDefault(parentId, List.of())) {
            if (department.id() == targetId) {
                return List.of(department);
            }
            List<DepartmentDto> childPath = findDepartmentPath(childrenByParent, department.id(), targetId);
            if (!childPath.isEmpty()) {
                List<DepartmentDto> path = new ArrayList<>();
                path.add(department);
                path.addAll(childPath);
                return path;
            }
        }
        return List.of();
    }

    private int nextSegmentIndex(String parentPrefix, List<DepartmentDto> siblings) {
        int maxExistingSegment = 0;
        for (DepartmentDto sibling : siblings) {
            Integer index = directChildSegmentIndex(parentPrefix, sibling.code());
            if (index != null) {
                maxExistingSegment = Math.max(maxExistingSegment, index);
            }
        }
        return Math.max(maxExistingSegment, siblings.size()) + 1;
    }

    private Integer directChildSegmentIndex(String parentPrefix, String code) {
        if (code == null || !code.startsWith(parentPrefix)) {
            return null;
        }
        String segment = code.substring(parentPrefix.length());
        if (!CODE_SEGMENT_PATTERN.matcher(segment).matches()) {
            return null;
        }
        return Integer.parseInt(segment.substring(1));
    }

    private String formatSegment(int index) {
        return "A%02d".formatted(index);
    }

    private static final class MutableDepartmentNode {
        private final DepartmentDto department;
        private final List<MutableDepartmentNode> children = new ArrayList<>();

        private MutableDepartmentNode(DepartmentDto department) {
            this.department = department;
        }

        private DepartmentDto toDto() {
            return new DepartmentDto(
                    department.id(),
                    department.parentId(),
                    department.code(),
                    department.name(),
                    department.status(),
                    department.sortOrder(),
                    children.stream().map(MutableDepartmentNode::toDto).toList());
        }
    }
}
