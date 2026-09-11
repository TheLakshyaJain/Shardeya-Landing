package com.shardeya.foundation.rbac;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "role")
public class Role {

    @Id
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "name_hi", nullable = false, length = 60)
    private String nameHi;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    // role_permission has no columns beyond the two key parts, so it's mapped as
    // a plain string collection rather than a separate @Entity.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissionCodes = new HashSet<>();

    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameHi() {
        return nameHi;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    public boolean hasPermission(String code) {
        return permissionCodes.contains(code);
    }
}
