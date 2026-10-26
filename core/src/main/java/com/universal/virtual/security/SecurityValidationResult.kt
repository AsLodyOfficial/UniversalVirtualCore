package com.universal.virtual.security

enum class SecurityValidationResult {
    ALLOWED,
    DENIED_USER_ISOLATION,
    DENIED_PACKAGE_IMPERSONATION,
    DENIED_INSTANCE_ISOLATION,
    DENIED_PROCESS_ISOLATION,
    DENIED_PATH_TRAVERSAL;

    val isAllowed: Boolean get() = this == ALLOWED
    val isDenied: Boolean get() = this != ALLOWED
}
