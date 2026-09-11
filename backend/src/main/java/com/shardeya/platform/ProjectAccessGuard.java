package com.shardeya.platform;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The real enforcement behind {@link ScopedToProject} — called explicitly from
 * service methods rather than via an AOP pointcut matching annotated
 * *parameters*. Spring AOP is proxy-based and does not reliably match
 * parameter-level annotations through a plain {@code execution()} pointcut
 * (that needs full AspectJ compile/load-time weaving, which this project
 * doesn't use); {@link PermissionAspect}'s {@code @annotation()} pointcut only
 * works because it matches a *method*-level annotation. Rather than ship an
 * untested pointcut for a security-relevant check — the exact class of bug
 * M1 kept finding in AOP/proxy edge cases — this is a plain, directly-called,
 * directly-testable guard.
 *
 * <p>Currently a no-op in practice: every M1/M2 role (BUILDER_ADMIN,
 * BROKER_OWNER) is an owner with {@code allProjects=true}. It becomes load-bearing
 * the moment M4 adds a restricted staff role with a real {@code project_scope}
 * list — wiring it now means M4 doesn't have to touch every M2 controller.
 */
@Component
public class ProjectAccessGuard {

    /** Same 404-not-403 rule as tenant isolation (CLAUDE.md rule #1) — a project outside your scope doesn't exist to you. */
    public void assertAccess(UUID projectId) {
        TenantContext.Tenant tenant = TenantContext.current();
        if (!tenant.allProjects() && !tenant.projectScope().contains(projectId)) {
            throw new ResourceNotFoundException("error.notFound");
        }
    }
}
