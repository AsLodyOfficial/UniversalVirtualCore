#include "page_size.hpp"
#include <unistd.h>
#include <sys/mman.h>
#include "logging.hpp"

namespace vcore::memory {

size_t PageSizeManager::GetRuntimePageSize() {
    long sz = sysconf(_SC_PAGESIZE);
    if (sz <= 0) {
        return 16384; // Safe default on Android 16
    }
    return static_cast<size_t>(sz);
}

bool PageSizeManager::Is16KbPageSize() {
    return GetRuntimePageSize() >= 16384;
}

uintptr_t PageSizeManager::AlignUp(uintptr_t addr, size_t pageSize) {
    return (addr + pageSize - 1) & ~(pageSize - 1);
}

uintptr_t PageSizeManager::AlignDown(uintptr_t addr, size_t pageSize) {
    return addr & ~(pageSize - 1);
}

bool PageSizeManager::ValidateElfLoadAlignment(uintptr_t baseAddr, size_t segmentAlignment) {
    size_t runtimePage = GetRuntimePageSize();
    size_t requiredAlign = (segmentAlignment > runtimePage) ? segmentAlignment : runtimePage;
    bool valid = (baseAddr % requiredAlign) == 0;
    if (!valid) {
        LOGW("ELF Load Segment unaligned! Base: 0x%zx, Required: 0x%zx", (size_t)baseAddr, requiredAlign);
    }
    return valid;
}

} // namespace vcore::memory
