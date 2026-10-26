#pragma once
#include <string>
#include <map>
#include <mutex>
#include <vector>

namespace vcore::fs {

class PathMapper {
public:
    static PathMapper& GetInstance();

    void AddRule(const std::string& virtualPrefix, const std::string& hostTarget);
    void RemoveRule(const std::string& virtualPrefix);
    std::string Resolve(const std::string& origPath);
    void Clear();

private:
    std::mutex mMutex;
    std::map<std::string, std::string> mRules;
};

} // namespace vcore::fs
