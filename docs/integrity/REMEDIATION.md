# Database, Isolation & Remediation Guide

## Database Invariants
Room Database v2/v3 stores packages, components, users, instances, processes, and diagnostic events. Relational foreign key cascades ensure no orphaned component rows exist.

## Isolation Invariants
Multi-user and multi-instance boundaries are enforced by `VirtualSecurityManager`. Attempts by one virtual user or instance to resolve another's sandbox directory are intercepted and rejected.

## Remediation Manager
Safe, non-destructive actions:
1. Re-generate missing instance sandbox directory trees.
2. Prune abandoned staging folders.
3. Reset stale virtual process states.
4. Clear historical crash logs.
