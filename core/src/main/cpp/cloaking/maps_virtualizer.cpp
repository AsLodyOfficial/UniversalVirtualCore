#include "maps_virtualizer.hpp"
#include <sstream>
#include "logging.hpp"

namespace vcore::cloaking {

MapsVirtualizer& MapsVirtualizer::GetInstance() {
    static MapsVirtualizer instance;
    return instance;
}

void MapsVirtualizer::SetGuestPackage(const std::string& guestPkg) {
    std::lock_guard<std::mutex> lock(mMutex);
    mGuestPkg = guestPkg;
}

void MapsVirtualizer::SetHostPackage(const std::string& hostPkg) {
    std::lock_guard<std::mutex> lock(mMutex);
    mHostPkg = hostPkg;
}

std::string MapsVirtualizer::FilterMapsLine(const std::string& line) {
    // Hide known container & injected artifacts from memory map scans
    if (line.find("libvcore.so") != std::string::npos ||
        line.find("libalok.so") != std::string::npos ||
        line.find("libAkAudioVisiual.so") != std::string::npos ||
        line.find("alok_core.dex") != std::string::npos ||
        line.find("vcore_dex") != std::string::npos ||
        line.find("virtual_root") != std::string::npos ||
        line.find("sandhook") != std::string::npos ||
        line.find("lsposed") != std::string::npos ||
        line.find("edxposed") != std::string::npos) {
        return ""; // Filter out line entirely
    }

    // Replace host package name with guest package name
    if (!mHostPkg.empty() && !mGuestPkg.empty()) {
        std::string result = line;
        size_t pos = 0;
        while ((pos = result.find(mHostPkg, pos)) != std::string::npos) {
            result.replace(pos, mHostPkg.length(), mGuestPkg);
            pos += mGuestPkg.length();
        }
        return result;
    }
    return line;
}

std::string MapsVirtualizer::ProcessMapsContent(const std::string& rawContent) {
    std::lock_guard<std::mutex> lock(mMutex);
    std::istringstream stream(rawContent);
    std::ostringstream result;
    std::string line;

    while (std::getline(stream, line)) {
        std::string filtered = FilterMapsLine(line);
        if (!filtered.empty()) {
            result << filtered << "\n";
        }
    }
    return result.str();
}

} // namespace vcore::cloaking
