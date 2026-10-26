#pragma once
#include <cstddef>
#include <cstdint>

namespace vcore::memory {

class PageSizeManager {
public:
    static size_t GetRuntimePageSize();
    static bool Is16KbPageSize();
    static uintptr_t AlignUp(uintptr_t addr, size_t pageSize);
    static uintptr_t AlignDown(uintptr_t addr, size_t pageSize);
    static bool ValidateElfLoadAlignment(uintptr_t baseAddr, size_t segmentAlignment);
};

} // namespace vcore::memory
