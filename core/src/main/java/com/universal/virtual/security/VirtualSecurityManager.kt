package com.universal.virtual.security

import com.universal.virtual.api.interfaces.IVirtualSecurityManager
import com.universal.virtual.common.identity.VirtualIdentity
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.identity.VirtualUserId
import java.io.File

class VirtualSecurityManager(
    override val policy: SecurityPolicy = SecurityPolicy()
) : IVirtualSecurityManager {

    override fun validateUserAccess(caller: VirtualUserId, target: VirtualUserId): SecurityValidationResult {
        if (!policy.enforceUserIsolation) return SecurityValidationResult.ALLOWED
        return if (caller == target) {
            SecurityValidationResult.ALLOWED
        } else {
            SecurityValidationResult.DENIED_USER_ISOLATION
        }
    }

    override fun validatePackageAccess(caller: VirtualPackageId, target: VirtualPackageId): SecurityValidationResult {
        if (!policy.enforcePackageIsolation) return SecurityValidationResult.ALLOWED
        return if (caller == target) {
            SecurityValidationResult.ALLOWED
        } else {
            SecurityValidationResult.DENIED_PACKAGE_IMPERSONATION
        }
    }

    override fun validateInstanceAccess(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult {
        val userCheck = validateUserAccess(caller.userId, target.userId)
        if (userCheck.isDenied) return userCheck

        val packageCheck = validatePackageAccess(caller.packageId, target.packageId)
        if (packageCheck.isDenied) return packageCheck

        if (!policy.enforceInstanceIsolation) return SecurityValidationResult.ALLOWED

        return if (caller.instanceId == target.instanceId) {
            SecurityValidationResult.ALLOWED
        } else {
            SecurityValidationResult.DENIED_INSTANCE_ISOLATION
        }
    }

    override fun validateProcessAccess(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult {
        val instanceCheck = validateInstanceAccess(caller, target)
        if (instanceCheck.isDenied) return instanceCheck

        if (!policy.enforceProcessIsolation) return SecurityValidationResult.ALLOWED

        return if (caller.processId == target.processId) {
            SecurityValidationResult.ALLOWED
        } else {
            SecurityValidationResult.DENIED_PROCESS_ISOLATION
        }
    }

    override fun validateIdentity(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult {
        return validateProcessAccess(caller, target)
    }

    override fun validatePathAccess(caller: VirtualIdentity, targetFile: File): SecurityValidationResult {
        if (!policy.blockPathTraversal) return SecurityValidationResult.ALLOWED

        val rawPath = targetFile.path
        if (rawPath.contains("..") || rawPath.contains("./") || rawPath.contains(".\\")) {
            return SecurityValidationResult.DENIED_PATH_TRAVERSAL
        }

        val canonicalPath = try {
            targetFile.canonicalPath
        } catch (_: Exception) {
            return SecurityValidationResult.DENIED_PATH_TRAVERSAL
        }

        if (canonicalPath.contains("/../") || canonicalPath.contains("\\..\\")) {
            return SecurityValidationResult.DENIED_PATH_TRAVERSAL
        }

        return SecurityValidationResult.ALLOWED
    }
}
