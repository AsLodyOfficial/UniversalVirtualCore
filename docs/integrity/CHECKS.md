# Integrity Checks & Categories

## Categories
- `ENVIRONMENT`: Core lifecycle, database connectivity, and backend health.
- `DATABASE`: Room schema version, foreign keys, table presence.
- `STORAGE`: Database ↔ Storage consistency, directory trees.
- `PACKAGE`: APK validation, manifest structure, metadata alignment.
- `USER`: Multi-user hierarchy, quota bounds.
- `INSTANCE`: Multi-instance isolation, parent package linking.
- `PROCESS`: Virtual PID allocation, stale state detection.
- `NATIVE`: ABI architecture validation (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
- `ISOLATION`: Sandbox boundaries, traversal rejection.
- `RECOVERY`: Incomplete transaction recovery and crash logs.
