#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <glog/logging.h>
#include <fmt/core.h>

#include "svdpi.h"
#include "vbridge_impl.h"
#include "exceptions.h"

static bool terminated = false;


void sigint_handler(int s) {
  terminated = true;
  dpiFinish();
}



#define TRY(action) \
  try {             \
    if (!terminated) {action}          \
  } catch (ReturnException &e) { \
    terminated = true;                \
    LOG(INFO) << fmt::format("detect returning instruction, gracefully quit simulation");                  \
    dpiFinish();    \
  } catch (std::runtime_error &e) { \
    terminated = true;                \
    LOG(ERROR) << fmt::format("detect exception ({}), gracefully abort simulation", e.what());                 \
    dpiError(e.what());  \
  }

#if VM_TRACE
void VBridgeImpl::dpiDumpWave() {
  TRY({
    ::dpiDumpWave((wave + ".fst").c_str());
  })
}
#endif

[[maybe_unused]] void dpiInitCosim() {
  std::signal(SIGINT, sigint_handler);
  svSetScope(svGetScopeFromName("TOP.TestBench.verificationModule.verbatim"));
  TRY({
    vbridge_impl_instance.dpiInitCosim();
  })
}


[[maybe_unused]] void dpiTimeoutCheck() {
  TRY({
    vbridge_impl_instance.timeoutCheck();
  })
}


