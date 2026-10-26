#include "virtual_fs.hpp"
#include <sys/stat.h>
#include <unistd.h>
#include "logging.hpp"

namespace vcore::fs {

bool VirtualFileSystem::EnsureDirectory(const std::string& path) {
    struct stat st;
    if (stat(path.c_str(), &st) == 0) {
        return S_ISDIR(st.st_mode);
    }
    return mkdir(path.c_str(), 0700) == 0;
}

bool VirtualFileSystem::InitializeUserStorage(int userId, const std::string& pkgName, const std::string& basePath) {
    std::string userDir = basePath + "/user/" + std::to_string(userId);
    std::string pkgDir = userDir + "/" + pkgName;
    
    EnsureDirectory(basePath);
    EnsureDirectory(basePath + "/user");
    EnsureDirectory(userDir);
    EnsureDirectory(pkgDir);
    EnsureDirectory(pkgDir + "/files");
    EnsureDirectory(pkgDir + "/databases");
    EnsureDirectory(pkgDir + "/shared_prefs");
    EnsureDirectory(pkgDir + "/cache");
    EnsureDirectory(pkgDir + "/lib");

    LOGI("Initialized virtual storage for user %d pkg %s at %s", userId, pkgName.c_str(), pkgDir.c_str());
    return true;
}

} // namespace vcore::fs
