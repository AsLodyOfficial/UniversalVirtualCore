# Storage Sandboxing & Layout

## Layout Tree
```
virtual/
├── packages/
│   └── <packageId>/
│       ├── apk/source.apk
│       ├── staging/
│       └── lib/<abi>/*.so
└── instances/
    └── <instanceId>/
        └── <packageId>/
            ├── files/
            ├── cache/
            ├── databases/
            └── shared_prefs/
```
All paths are strictly isolated and verified against path traversal attacks.
