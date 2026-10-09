// Generated from services/smartfarm-identity-service/src/main/resources/application.yml
// Run connect-smartfarm-authorization-edit-delete-consistency.py after changing the YAML catalog.
export type ConfiguredPermission = { code:string; resourceType:string; action:string; description:string }
export const CONFIGURED_PERMISSIONS: ConfiguredPermission[] = [
  {
    "code": "farm:read",
    "resourceType": "farm",
    "action": "read",
    "description": "Allows read access to farm"
  },
  {
    "code": "health:read",
    "resourceType": "health",
    "action": "read",
    "description": "Allows read access to health"
  },
  {
    "code": "identity:mfa:disable",
    "resourceType": "identity-mfa",
    "action": "disable",
    "description": "Allows disable access to identity mfa"
  },
  {
    "code": "identity:mfa:enroll",
    "resourceType": "identity-mfa",
    "action": "enroll",
    "description": "Allows enroll access to identity mfa"
  },
  {
    "code": "identity:mfa:recovery:regenerate",
    "resourceType": "identity-mfa-recovery",
    "action": "regenerate",
    "description": "Allows regenerate access to identity mfa recovery"
  },
  {
    "code": "identity:permission:read",
    "resourceType": "identity-permission",
    "action": "read",
    "description": "Allows read access to identity permission"
  },
  {
    "code": "identity:principal:read",
    "resourceType": "identity-principal",
    "action": "read",
    "description": "Allows read access to identity principal"
  },
  {
    "code": "identity:principal:update",
    "resourceType": "identity-principal",
    "action": "update",
    "description": "Allows update access to identity principal"
  },
  {
    "code": "identity:role:read",
    "resourceType": "identity-role",
    "action": "read",
    "description": "Allows read access to identity role"
  },
  {
    "code": "identity:security:read",
    "resourceType": "identity-security",
    "action": "read",
    "description": "Allows read access to identity security"
  },
  {
    "code": "identity:user:create",
    "resourceType": "identity-user",
    "action": "create",
    "description": "Allows create access to identity user"
  },
  {
    "code": "identity:user:credential:reset",
    "resourceType": "identity-user-credential",
    "action": "reset",
    "description": "Allows reset access to identity user credential"
  },
  {
    "code": "identity:user:mfa:reset",
    "resourceType": "identity-user-mfa",
    "action": "reset",
    "description": "Allows reset access to identity user mfa"
  },
  {
    "code": "identity:user:read",
    "resourceType": "identity-user",
    "action": "read",
    "description": "Allows read access to identity user"
  },
  {
    "code": "inventory:read",
    "resourceType": "inventory",
    "action": "read",
    "description": "Allows read access to inventory"
  },
  {
    "code": "inventory:write",
    "resourceType": "inventory",
    "action": "write",
    "description": "Allows write access to inventory"
  },
  {
    "code": "orders:read",
    "resourceType": "orders",
    "action": "read",
    "description": "Allows read access to orders"
  },
  {
    "code": "orders:saga:admin",
    "resourceType": "orders-saga",
    "action": "admin",
    "description": "Allows admin access to orders saga"
  },
  {
    "code": "orders:write",
    "resourceType": "orders",
    "action": "write",
    "description": "Allows write access to orders"
  },
  {
    "code": "report:read",
    "resourceType": "report",
    "action": "read",
    "description": "Allows read access to report"
  },
  {
    "code": "report:write",
    "resourceType": "report",
    "action": "write",
    "description": "Allows write access to report"
  },
  {
    "code": "tasks:write",
    "resourceType": "tasks",
    "action": "write",
    "description": "Allows write access to tasks"
  },
  {
    "code": "identity:permission:manage",
    "resourceType": "identity-permission",
    "action": "manage",
    "description": "Allows manage access to identity permission"
  },
  {
    "code": "identity:role:assign",
    "resourceType": "identity-role",
    "action": "assign",
    "description": "Allows assign access to identity role"
  },
  {
    "code": "identity:role:manage",
    "resourceType": "identity-role",
    "action": "manage",
    "description": "Allows manage access to identity role"
  },
  {
    "code": "identity:user:disable",
    "resourceType": "identity-user",
    "action": "disable",
    "description": "Allows disable access to identity user"
  },
  {
    "code": "identity:user:profile:manage",
    "resourceType": "identity-user-profile",
    "action": "manage",
    "description": "Allows SUPERADMIN to update another user profile"
  },
  {
    "code": "identity:user:account:manage",
    "resourceType": "identity-user-account",
    "action": "manage",
    "description": "Allows SUPERADMIN to disable, enable and unlock accounts"
  }
]
