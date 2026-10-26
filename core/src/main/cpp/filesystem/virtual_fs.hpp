#pragma once
#include <string>

namespace vcore::fs {

class VirtualFileSystem {
public:
    static bool InitializeUserStorage(int userId, const std::string& pkgName, const std::string& basePath);
    static bool EnsureDirectory(const std::string& path);
};

} // namespace vcore::fs
