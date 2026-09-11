package com.shardeya.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative RBAC enforcement (CLAUDE.md rule #5: "RBAC is server-side... UI
 * hiding is cosmetic"). {@link PermissionAspect} checks the caller's
 * {@link TenantContext}-carried permission set before the method body runs.
 * Not endpoint-driven — apply to the service/controller method that performs
 * the guarded action, per 02-FOUNDATION-MODULES.md M-02 §4.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    String value();
}
