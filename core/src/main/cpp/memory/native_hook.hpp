#pragma once
#include <cstdint>
#include <string>

namespace vcore::memory {

class NativeHook {
public:
    static uintptr_t FindSymbolAddress(const std::string& libraryName, const std::string& symbolName);
    static bool PatchMemory(uintptr_t address, const uint8_t* newCode, size_t length);
};

} // namespace vcore::memory
