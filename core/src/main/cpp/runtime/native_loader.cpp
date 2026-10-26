#include "native_loader.hpp"
#include <sys/system_properties.h>
#include "logging.hpp"

namespace vcore::runtime {

std::string NativeLoader::GetPrimaryAbi() {
    char abi[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.product.cpu.abi", abi);
    if (abi[0] != '\0') {
        return std::string(abi);
    }
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#else
    return "x86";
#endif
}

bool NativeLoader::ValidateLibraryCompatibility(const std::string& libPath) {
    LOGD("Validating native library: %s for ABI %s", libPath.c_str(), GetPrimaryAbi().c_str());
    return true;
}

} // namespace vcore::runtime
