package com.gongwen.assistant.organization;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserAdminController {
    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<List<UserAdminDto>> list() {
        return ApiResponse.ok(userAdminService.list());
    }

    @PostMapping
    public ApiResponse<UserAdminDto> create(@RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAdminDto> update(@PathVariable long id, @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userAdminService.update(id, request));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id, @RequestBody ResetUserPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        userAdminService.disable(id);
        return ApiResponse.ok(null);
    }

    @ExceptionHandler(UserAdminException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserAdminException(UserAdminException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
