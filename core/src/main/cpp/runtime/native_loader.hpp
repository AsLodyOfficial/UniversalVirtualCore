#pragma once
#include <string>
#include <vector>

namespace vcore::runtime {

class NativeLoader {
public:
    static std::string GetPrimaryAbi();
    static bool ValidateLibraryCompatibility(const std::string& libPath);
};

} // namespace vcore::runtime
