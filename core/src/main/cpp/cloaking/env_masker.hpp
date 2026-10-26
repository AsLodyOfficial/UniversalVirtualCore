#pragma once
#include <string>

namespace vcore::cloaking {

class EnvMasker {
public:
    static bool EnableAntiDetection();
    static bool MaskTracerPid();
    static bool IsRootBlockedPath(const std::string& path);
};

} // namespace vcore::cloaking
