# Severity & Health Scoring

## Severity Levels
- `PASS`: Subsystem is fully compliant.
- `INFO`: Informational state report.
- `WARNING`: Potential inconsistency requiring user attention.
- `ERROR`: Verified defect or divergence in database/storage/metadata.
- `CRITICAL`: Security boundary, isolation failure, or database corruption.

## Health Score Calculation
- `100`: Healthy (0 errors, 0 warnings).
- `70-99`: Healthy with warnings.
- `10-69`: Degraded or corrupted.
- `0`: Critical isolation failure.
