# Integrity Hashing, Testing & Troubleshooting

## Hashing
SHA-256 hashes are computed dynamically by `IntegrityHashStore` and verified against trusted installation baselines.

## Testing Suite
Unit tests under `core/src/test/java/com/universal/virtual/integrity/` test:
- Database schema and foreign key consistency
- Storage layouts and path traversal blocks
- Package metadata and binary validation
- Multi-user and multi-instance isolation
- Scan engine execution and progress reporting
- Report diffing and JSON/TXT/CSV export

## Troubleshooting
If a check fails:
1. Review the evidence key-value pairs in `IntegrityCheckResult`.
2. Check if a repair action is available in `IntegrityRemediationManager`.
3. Re-run scan to confirm health restoration.
