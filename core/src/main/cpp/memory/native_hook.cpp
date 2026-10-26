#include "native_hook.hpp"
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cstring>
#include "page_size.hpp"
#include "logging.hpp"

namespace vcore::memory {

uintptr_t NativeHook::FindSymbolAddress(const std::string& libraryName, const std::string& symbolName) {
    void* handle = dlopen(libraryName.empty() ? nullptr : libraryName.c_str(), RTLD_NOW);
    if (!handle) {
        LOGE("Failed to dlopen %s: %s", libraryName.c_str(), dlerror());
        return 0;
    }
    void* sym = dlsym(handle, symbolName.c_str());
    if (!sym) {
        LOGE("Failed to find symbol %s: %s", symbolName.c_str(), dlerror());
        return 0;
    }
    return reinterpret_cast<uintptr_t>(sym);
}

bool NativeHook::PatchMemory(uintptr_t address, const uint8_t* newCode, size_t length) {
    if (!address || !newCode || length == 0) return false;

    size_t pageSize = PageSizeManager::GetRuntimePageSize();
    uintptr_t pageStart = PageSizeManager::AlignDown(address, pageSize);
    size_t protectLen = PageSizeManager::AlignUp(address + length, pageSize) - pageStart;

    if (mprotect(reinterpret_cast<void*>(pageStart), protectLen, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect failed on address 0x%zx", (size_t)address);
        return false;
    }

    std::memcpy(reinterpret_cast<void*>(address), newCode, length);

    // Restore protections & flush instruction cache on ARM
    mprotect(reinterpret_cast<void*>(pageStart), protectLen, PROT_READ | PROT_EXEC);
#if defined(__aarch64__) || defined(__arm__)
    __builtin___clear_cache(
        reinterpret_cast<char*>(address),
        reinterpret_cast<char*>(address + length)
    );
#endif

    LOGI("Patched memory at 0x%zx (length: %zu bytes)", (size_t)address, length);
    return true;
}

} // namespace vcore::memory
