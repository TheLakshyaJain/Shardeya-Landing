package com.shardeya.foundation.rbac;

import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RbacController {

    private final RoleRepository roleRepository;
    private final TenantContextBinder tenantContextBinder;

    public RbacController(RoleRepository roleRepository, TenantContextBinder tenantContextBinder) {
        this.roleRepository = roleRepository;
        this.tenantContextBinder = tenantContextBinder;
    }

    @GetMapping("/api/v1/permissions/me")
    public PermissionsMeResponse permissionsMe() {
        var tenant = tenantContextBinder.current();
        List<String> permissions = tenant.permissions().stream().sorted().toList();
        String scopeMode = tenant.allProjects() ? "ALL" : "SCOPED";
        return new PermissionsMeResponse(permissions, tenant.projectScope(), scopeMode);
    }

    // M4: a plain "BUILDER_"/"BROKER_" code-prefix match was correct back
    // when each org type had exactly one role (BUILDER_ADMIN, BROKER_OWNER)
    // -- it silently broke the moment the four M4 staff roles (MANAGER,
    // SALES_EXECUTIVE, ACCOUNTS_STAFF, VIEW_ONLY) were seeded, since none of
    // those codes start with "BUILDER_" even though all four are
    // builder-only (M-02 §3's "five roles exactly as §18.3" is a builder
    // concept; brokers have no staff roles yet). An explicit code list per
    // org type is what the catalogue actually is, not a naming convention.
    private static final List<String> BUILDER_ROLE_CODES =
            List.of("BUILDER_ADMIN", "MANAGER", "SALES_EXECUTIVE", "ACCOUNTS_STAFF", "VIEW_ONLY");
    private static final List<String> BROKER_ROLE_CODES = List.of("BROKER_OWNER");

    @GetMapping("/api/v1/roles")
    public List<RoleSummary> roles() {
        var tenant = tenantContextBinder.current();
        List<String> codes = "BUILDER".equals(tenant.orgType()) ? BUILDER_ROLE_CODES : BROKER_ROLE_CODES;
        return roleRepository.findByOrgIdIsNullAndCodeIn(codes).stream()
                .map(r -> new RoleSummary(r.getCode(), r.getNameEn(), r.getNameHi()))
                .toList();
    }

    @GetMapping("/api/v1/roles/{code}/permissions")
    public List<String> rolePermissions(@PathVariable String code) {
        Role role = roleRepository.findByCodeAndOrgIdIsNull(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notFound"));
        return role.getPermissionCodes().stream().sorted().toList();
    }
}
