#pragma once
#include <string>
#include <vector>
#include <mutex>
#include <cstdint>

namespace vcore {
namespace diagnostics {

struct MemoryStats {
    uint64_t vm_peak_kb = 0;
    uint64_t vm_rss_kb = 0;
    uint64_t vm_size_kb = 0;
    uint64_t page_size_bytes = 16384;
};

struct PerformanceStats {
    float average_fps = 60.0f;
    float frame_time_ms = 16.6f;
    uint64_t active_threads = 1;
};

class DiagnosticEngine {
public:
    static DiagnosticEngine& get();

    void initialize(const std::string& logDirectory);
    void startMonitoring();
    void stopMonitoring();
    void updateFrame(float deltaTime);

    MemoryStats getMemoryStats();
    PerformanceStats getPerformanceStats();
    std::string generateJSONReport(const std::string& appName, int activeContainers);

    void installCrashHandler();
    void logEvent(const std::string& category, const std::string& message);

private:
    DiagnosticEngine() = default;
    ~DiagnosticEngine() = default;

    std::string mLogDir;
    bool mInitialized = false;
    bool mMonitoring = false;
    std::mutex mMutex;

    // Performance tracking
    float mTotalTime = 0.0f;
    uint64_t mFrameCount = 0;
    float mFpsAccumulator = 60.0f;
};

} // namespace diagnostics
} // namespace vcore