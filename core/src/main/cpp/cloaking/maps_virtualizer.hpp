#pragma once
#include <string>
#include <vector>
#include <mutex>

namespace vcore::cloaking {

class MapsVirtualizer {
public:
    static MapsVirtualizer& GetInstance();
    void SetGuestPackage(const std::string& guestPkg);
    void SetHostPackage(const std::string& hostPkg);
    std::string FilterMapsLine(const std::string& line);
    std::string ProcessMapsContent(const std::string& rawContent);

private:
    std::mutex mMutex;
    std::string mGuestPkg;
    std::string mHostPkg;
};

} // namespace vcore::cloaking
