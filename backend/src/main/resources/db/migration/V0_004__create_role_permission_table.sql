CREATE TABLE role_permission (
    role_id         UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (role_id, permission_code)
);

-- No org_id: scoped transitively through role_id. role's own RLS policy already
-- restricts which roles (and thus which role_permission rows a join can reach)
-- a tenant can see.
