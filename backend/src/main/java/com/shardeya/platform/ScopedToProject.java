package com.shardeya.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a project id that must be checked against the
 * caller's {@code project_scope} (00-ARCHITECTURE.md §2.2). <b>Not yet
 * enforced</b> — there's no {@code project} table until M2 (Builder: Projects
 * &amp; Plot Grid), so there's nothing real to scope against yet. The
 * annotation exists now so M2 can wire the actual check into
 * {@link PermissionAspect} without touching every controller that will need
 * it. Applying this in M1 is a no-op by design, not a bug.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ScopedToProject {
}
