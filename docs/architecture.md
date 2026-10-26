# UniversalVirtualCore Architecture Specification

UniversalVirtualCore is an extensible, rootless Android application virtualization platform. It is engineered with a modular, capability-driven architecture that abstracts virtual container execution from the underlying host platform and backend implementation.

## 1. High-Level Architecture

`	ext
+-------------------------------------------------------------+
|                      Host Application                       |
|           (Main Activity / Admin & Diagnostics UI)          |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                  UniversalVirtualCore (API)                 |
|   +---------------------+         +---------------------+   |
|   |   CoreVersion       |         |   CoreState         |   |
|   |   (0.1.0-alpha)     |         |   (Lifecycle FSM)   |   |
|   +---------------------+         +---------------------+   |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                     VirtualCoreRuntime                      |
|                  (Orchestrator Subsystem)                   |
|                                                             |
|   +-------------------+  +-------------------+  +-------+   |
|   | VirtualEventBus   |  | SecurityManager   |  | Diag  |   |
|   | (Sync/Async Bus)  |  | (Isolation Policy)|  | Engine|   |
|   +-------------------+  +-------------------+  +-------+   |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                 Backend Abstraction Layer                   |
|                  (IVirtualizationBackend)                   |
|                                                             |
|   +-----------------------------------------------------+   |
|   | FrameworkContainerBackend (Phase 1 Initial Backend)  |   |
|   +-----------------------------------------------------+   |
|   | FutureVmBackend (AVF / Microdroid / pKVM Future)    |   |
|   +-----------------------------------------------------+   |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                   Future Subsystems (Phase 2+)              |
|                                                             |
|  - Package Manager & APK Ingestion (Phase 2-3)              |
|  - Virtual Storage & Path Sandbox (Phase 4)                 |
|  - Multi-User & Instance Isolation (Phase 5-6)              |
|  - ClassLoader & Context Injection (Phase 7-8)              |
|  - Component Runtimes: Activity, Service, Provider, BC (9-13)
|  - Native Runtime Bridge (Phase 15)                         |
+-------------------------------------------------------------+
`

## 2. Universal Identity Model

Every virtual entity in UniversalVirtualCore is addressed by a strictly typed, immutable 4-tuple:

\text{VirtualIdentity} = (\text{VirtualUserId}, \text{VirtualPackageId}, \text{VirtualInstanceId}, \text{VirtualProcessId})

- **VirtualUserId**: Represents isolated user partitions (User 0, User 1, ...).
- **VirtualPackageId**: Validated Android package identifier (e.g., com.example.app).
- **VirtualInstanceId**: Multi-instance clone index (Instance 0, Instance 1, ...).
- **VirtualProcessId**: Distinct virtual process slot, isolated from host OS Linux PID.

This prevents ambiguity between:
- User 0 / com.example.app / Instance 0
- User 0 / com.example.app / Instance 1
- User 1 / com.example.app / Instance 0

## 3. Deterministic Lifecycle State Machine

The core state machine governs platform initialization and shutdown with strict transition guards:

`	ext
      +------------------+
      |  UNINITIALIZED   |<--------------------+
      +------------------+                     |
               |                               |
               v (initialize)                  |
      +------------------+                     |
      |   INITIALIZING   |--------+            |
      +------------------+        |            |
               |                  | (error)    |
               v (success)        v            |
      +------------------+   +----------+      |
      |      READY       |   |  FAILED  |      |
      +------------------+   +----------+      |
               |                               |
               v (shutdown)                    |
      +------------------+                     |
      |     STOPPING     |                     |
      +------------------+                     |
               |                               |
               v (complete)                    |
      +------------------+                     |
      |     SHUTDOWN     |---------------------+
      +------------------+
`

## 4. Security Isolation Model

The security layer enforces a closed-by-default boundary:
1. **User Isolation**: Caller cannot cross user boundaries unless holding SYSTEM authority.
2. **Package Isolation**: Packages cannot impersonate or hijack components from another package ID.
3. **Instance Isolation**: App instances maintain strict isolation of state and file namespaces.
4. **Path Traversal Protection**: Directory traversal patterns (.., canonical path escaping) are blocked before file I/O occurs.

## 5. Native Runtime Bridge

The native module (:native) provides a JNI bridge (NativeRuntimeBridge) for architecture discovery, ABI verification, and low-level runtime integration without performing unsafe hooking or security-product evasion.
