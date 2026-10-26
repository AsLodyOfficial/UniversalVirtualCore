#include "path_mapper.hpp"
#include "logging.hpp"

namespace vcore::fs {

PathMapper& PathMapper::GetInstance() {
    static PathMapper instance;
    return instance;
}

void PathMapper::AddRule(const std::string& virtualPrefix, const std::string& hostTarget) {
    std::lock_guard<std::mutex> lock(mMutex);
    mRules[virtualPrefix] = hostTarget;
    LOGI("PathMapper added: [%s] -> [%s]", virtualPrefix.c_str(), hostTarget.c_str());
}

void PathMapper::RemoveRule(const std::string& virtualPrefix) {
    std::lock_guard<std::mutex> lock(mMutex);
    mRules.erase(virtualPrefix);
}

std::string PathMapper::Resolve(const std::string& origPath) {
    std::lock_guard<std::mutex> lock(mMutex);
    for (const auto& [prefix, target] : mRules) {
        if (origPath.rfind(prefix, 0) == 0) {
            std::string resolved = target + origPath.substr(prefix.length());
            LOGD("PathMapper resolved: [%s] => [%s]", origPath.c_str(), resolved.c_str());
            return resolved;
        }
    }
    return origPath;
}

void PathMapper::Clear() {
    std::lock_guard<std::mutex> lock(mMutex);
    mRules.clear();
}

} // namespace vcore::fs
