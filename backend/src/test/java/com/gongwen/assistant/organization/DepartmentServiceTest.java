package com.gongwen.assistant.organization;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartmentServiceTest {
    private final InMemoryDepartmentRepository repository = new InMemoryDepartmentRepository();
    private final DepartmentService service = new DepartmentService(repository);

    @Test
    void listsDepartmentsAsTree() {
        repository.departments.add(new DepartmentDto(1L, null, "ROOT", "Head Office", "ACTIVE", 1, List.of()));
        repository.departments.add(new DepartmentDto(2L, 1L, "ADMIN", "Admin Office", "ACTIVE", 2, List.of()));
        repository.departments.add(new DepartmentDto(3L, 2L, "ARCHIVE", "Archive Team", "ACTIVE", 3, List.of()));

        List<DepartmentDto> tree = service.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().children()).extracting(DepartmentDto::code).containsExactly("ADMIN");
        assertThat(tree.getFirst().children().getFirst().children()).extracting(DepartmentDto::code)
                .containsExactly("ARCHIVE");
    }

    @Test
    void createsDepartmentWithGeneratedHierarchicalCodeAndNormalizedName() {
        repository.departments.add(new DepartmentDto(1L, null, "ROOT", "Head Office", "ACTIVE", 1, List.of()));

        DepartmentDto created = service.create(new CreateDepartmentRequest(1L, null, " General Office ", 5));

        assertThat(created.code()).isEqualTo("A01A01");
        assertThat(created.name()).isEqualTo("General Office");
        assertThat(created.parentId()).isEqualTo(1L);
    }

    @Test
    void createsNextDepartmentCodeFromExistingSiblingSegments() {
        repository.departments.add(new DepartmentDto(1L, null, "A01", "Head Office", "ACTIVE", 1, List.of()));
        repository.departments.add(new DepartmentDto(2L, 1L, "A01A01", "Admin Office", "ACTIVE", 2, List.of()));

        DepartmentDto created = service.create(new CreateDepartmentRequest(1L, "IGNORED", "Finance Office", 5));

        assertThat(created.code()).isEqualTo("A01A02");
    }

    @Test
    void blocksDeletingDepartmentWithChildrenOrUsers() {
        repository.childCount = 1;
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(DepartmentException.class)
                .hasMessageContaining("child departments");

        repository.childCount = 0;
        repository.userCount = 2;
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(DepartmentException.class)
                .hasMessageContaining("users");

        repository.userCount = 0;
        repository.businessReferenceCount = 1;
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(DepartmentException.class)
                .hasMessageContaining("business data");
    }

    @Test
    void deletesDepartmentWhenItHasNoChildrenOrUsers() {
        service.delete(1L);

        assertThat(repository.deletedDepartmentId).isEqualTo(1L);
    }

    private static final class InMemoryDepartmentRepository implements DepartmentRepository {
        private final List<DepartmentDto> departments = new ArrayList<>();
        private long nextId = 10L;
        private int childCount;
        private int userCount;

        @Override
        public List<DepartmentDto> findAll() {
            return departments;
        }

        @Override
        public DepartmentDto create(Long parentId, String code, String name, int sortOrder) {
            DepartmentDto department = new DepartmentDto(nextId++, parentId, code, name, "ACTIVE", sortOrder, List.of());
            departments.add(department);
            return department;
        }

        @Override
        public Optional<DepartmentDto> update(long id, Long parentId, String name, String status, int sortOrder) {
            return Optional.empty();
        }

        @Override
        public int countChildren(long id) {
            return childCount;
        }

        @Override
        public int countUsers(long id) {
            return userCount;
        }

        @Override
        public int countBusinessReferences(long id) {
            return businessReferenceCount;
        }

        @Override
        public boolean delete(long id) {
            deletedDepartmentId = id;
            return true;
        }

        private Long deletedDepartmentId;
        private int businessReferenceCount;
    }
}
