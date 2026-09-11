package com.shardeya.platform;

import org.springframework.stereotype.Component;

/** Test fixture for {@link PermissionAspectTest} — a real Spring bean so AOP proxying applies. */
@Component
class PermissionGuardedFixture {

    @RequiresPermission("TEST_PERMISSION")
    String protectedAction() {
        return "ok";
    }
}
