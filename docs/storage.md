# UniversalVirtualCore — Storage & Isolation Architecture

## Storage Directory Layout

All virtual packages, users, and instance states are isolated within the private application directory:

`
/data/data/com.universal.virtual/files/
└── virtual/
    ├── packages/
    │   └── <packageId>/
    │       ├── source.apk              # Canonical imported APK
    │       ├── lib/                    # Extracted native libraries
    │       │   ├── arm64-v8a/
    │       │   │   └── libnative.so
    │       │   └── armeabi-v7a/
    │       └── staging/                # Transient directory for atomic commits
    │
    └── users/
        └── user_<userId>/
            └── <packageId>/
                ├── data/               # Virtual /data/data/<package>/
                │   ├── cache/
                │   ├── files/
                │   └── databases/
                └── external/           # Virtual Scoped External Storage
`

## Security & Path Traversal Mitigations

1. **Canonical Path Verification**: All operations in PackageStorageManager verify that the target canonical path begins with the allowed root directory boundary before reading, writing, or extracting files.
2. **Atomic Staging & Rollback**: APKs are copied first to a temporary staging file, size and checksum are verified, and atomic rename is executed. If database insertion or extraction fails, rollback safely cleans up.
3. **Cascading File Deletion**: Package removal deletes both the database records (cascading across all component tables) and the physical directory recursively.