package com.maimai.admin.controller;

import com.maimai.admin.dto.AdminDtos.UserItem;
import com.maimai.admin.dto.GovernanceDtos.RoleChange;
import com.maimai.admin.service.AdminRoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminRoleController {
    private final AdminRoleService roles;
    public AdminRoleController(AdminRoleService roles) {this.roles=roles;}
    @PostMapping("/{id}/roles") public UserItem change(@PathVariable long id,@RequestBody @Valid RoleChange request) {return roles.change(id,request);}
}
