# UniversalVirtualCore — Virtual Package Manager (Phase 2)

## Overview
The **Virtual Package Manager** subsystem is responsible for:
1. Validating input APK files (ApkValidator).
2. Parsing manifest metadata, application information, declared components (Activities, Services, ContentProviders, BroadcastReceivers), permissions, features, and native shared libraries (ApkParser).
3. Staging and committing APK files into isolated storage structures (PackageStorageManager).
4. Persisting packages and their component structures into an ACID-compliant Room SQLite database (UniversalVirtualDatabase and VirtualPackageRepository).
5. Emitting typed lifecycle events (VirtualEventBus) for package installations, updates, and removals.

---

## Architectural Workflow

`
[ Input APK Path ]
        │
        ▼
[ ApkValidator ] ─── (Existence, size limits, ZIP integrity, AndroidManifest presence)
        │
        ▼
[ ApkParser ] ────── (PackageArchiveInfo or SimpleAxmlReader fallback + ZIP lib/ ABI scan)
        │
        ▼
[ PackageStorageManager ]
   ├── Stage: virtual/packages/<packageId>/staging/
   ├── Atomic Commit: virtual/packages/<packageId>/source.apk
   └── Native Libs: virtual/packages/<packageId>/lib/<abi>/
        │
        ▼
[ VirtualPackageRepository ]
   └── @Transaction Atomic Database Operations (Package + 7 Component Tables)
        │
        ▼
[ VirtualEventBus ] ── (PACKAGE_INSTALLED / PACKAGE_UPDATED / PACKAGE_UNINSTALLED)
`

---

## Database Schema (Room)

- **irtual_packages**: Core metadata (packageName, label, versionName, versionCode, minSdk, targetSdk, apkPath, primaryAbi, isEnabled, installedAt).
- **irtual_activities**: Activity components (name, label, launchMode, exported, enabled, permission, theme, screenOrientation, configChanges, intentActions).
- **irtual_services**: Service components (name, permission, exported, enabled, foregroundServiceType, isolatedProcess).
- **irtual_providers**: ContentProvider components (name, authority, exported, enabled, grantUriPermissions, readPermission, writePermission, multiprocess).
- **irtual_receivers**: BroadcastReceiver components (name, permission, exported, enabled, intentActions).
- **irtual_permissions**: Requested/declared permissions (name, protectionLevel).
- **irtual_features**: Hardware/software features (name, required).
- **irtual_native_libraries**: ABI binaries (abi, libraryName, fileSizeBytes, extractedPath).

Foreign keys are configured with CASCADE delete, ensuring atomic cleanup when a package is uninstalled.