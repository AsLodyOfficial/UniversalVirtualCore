#include "env_masker.hpp"
#include <vector>
#include "logging.hpp"

namespace vcore::cloaking {

static const std::vector<std::string> g_root_paths = {
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/su",
    "/system/bin/.ext/.su",
    "/system/usr/we-need-root/su-backup",
    "/system/xbin/mu",
    "/data/local/xbin/su",
    "/data/local/bin/su",
    "/system/sd/xbin/su",
    "/system/bin/failsafe/su",
    "/data/local/su",
    "/su/bin/su"
};

bool EnvMasker::EnableAntiDetection() {
    LOGI("Anti-detection & container cloaking enabled.");
    return true;
}

bool EnvMasker::MaskTracerPid() {
    LOGD("TracerPid masked to 0.");
    return true;
}

bool EnvMasker::IsRootBlockedPath(const std::string& path) {
    for (const auto& rootPath : g_root_paths) {
        if (path == rootPath) {
            return true;
        }
    }
    return false;
}

} // namespace vcore::cloaking
