package com.shardeya.platform;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission)
            throws Throwable {
        String required = requiresPermission.value();
        TenantContext.Tenant tenant = TenantContext.current();

        if (!tenant.permissions().contains(required)) {
            throw new ForbiddenException("error.permission.denied." + required.toLowerCase());
        }

        return joinPoint.proceed();
    }
}
