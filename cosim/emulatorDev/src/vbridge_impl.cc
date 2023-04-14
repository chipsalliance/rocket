#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "verilated.h"

#include "glog_exception_safe.h"
#include "exceptions.h"
#include "util.h"
#include "vbridge_impl.h"

#include "simple_sim.h"
#include "util.h"


/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) {
  return 1 << encoded_size;
}

VBridgeImpl::VBridgeImpl() : sim(1 << 30),
                             isa("rv64gc", "msu"),
                             _cycles(100),
                             proc(
                                 /*isa*/ &isa,
                                 /*varch*/ fmt::format("").c_str(),
                                 /*sim*/ &sim,
                                 /*id*/ 0,
                                 /*halt on reset*/ true,
                                 /* endianness*/ memif_endianness_little,
                                 /*log_file_t*/ nullptr,
                                 /*sout*/ std::cerr) {
}

void VBridgeImpl::init_spike() {
  proc.reset();
  auto state = proc.get_state();
  LOG(INFO) << fmt::format("Spike reset misa={:08X}", state->misa->read());
  LOG(INFO) << fmt::format("Spike reset mstatus={:08X}", state->mstatus->read());
  // load binary to reset_vector
  sim.load(bin, ebin, reset_vector);
  LOG(INFO) << fmt::format("Simulation Environment Initialized: bin={}, wave={}, reset_vector={:#x}",
                           bin, wave, reset_vector);
}

void VBridgeImpl::loop_until_se_queue_full() {
  LOG(INFO) << fmt::format("Refilling Spike queue");
  while (to_rtl_queue.size() < to_rtl_queue_size) {
    try {
      std::optional<SpikeEvent> spike_event = spike_step();
      if (spike_event.has_value()) {
        SpikeEvent &se = spike_event.value();
        to_rtl_queue.push_front(std::move(se));
      }
    } catch (trap_t &trap) {
      LOG(FATAL) << fmt::format("spike trapped with {}", trap.name());
    }
  }
  LOG(INFO) << fmt::format("to_rtl_queue is full now, start to simulate.");
  for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
    LOG(INFO) << fmt::format("List: spike pc = {:08X}, write reg({}) from {:08x} to {:08X}, is commit:{}",
                             se_iter->pc, se_iter->rd_idx, se_iter->rd_old_bits, se_iter->rd_new_bits, se_iter->is_committed);
  }
}

// now we take all the instruction as spike event except csr insn
std::optional<SpikeEvent> VBridgeImpl::create_spike_event(insn_fetch_t fetch) {
  return SpikeEvent{proc, fetch, this};
}

// don't creat spike event for csr insn
// todo: haven't created spike event for insn which traps during fetch stage;
// dealing with trap:
// most traps are dealt by Spike when [proc.step(1)];
// traps during fetch stage [fetch = proc.get_mmu()->load_insn(state->pc)] are dealt manually using try-catch block below.
std::optional<SpikeEvent> VBridgeImpl::spike_step() {
  auto state = proc.get_state();
  // to use pro.state, set some csr
  state->dcsr->halt = false;
  // record pc before execute
  auto pc_before = state->pc;
  try {
    auto fetch = proc.get_mmu()->load_insn(state->pc);
    auto event = create_spike_event(fetch);
    auto &xr = proc.get_state()->XPR;
    LOG(INFO) << fmt::format("Spike start to execute pc=[{:08X}] insn = {:08X} DISASM:{}",
                             pc_before, fetch.insn.bits(), proc.get_disassembler()->disassemble(fetch.insn));
    auto &se = event.value();
    se.pre_log_arch_changes();
    proc.step(1);
    se.log_arch_changes();
    // todo: detect exactly the trap
    // if a insn_after_pc = 0x80000004,set it as committed
    // set insn which traps as committed in case the queue stalls
    if (state->pc == 0x80000004) {
      se.is_trap = true;
      LOG(INFO) << fmt::format("Trap happens at pc = {:08X} ", pc_before);
    }
    LOG(INFO) << fmt::format("Spike after execute pc={:08X} ", state->pc);
    return event;
  } catch (trap_t &trap) {
    LOG(INFO) << fmt::format("spike fetch trapped with {}", trap.name());
    proc.step(1);
    LOG(INFO) << fmt::format("Spike mcause={:08X}", state->mcause->read());
    return {};
  } catch (triggers::matched_t &t) {
    LOG(INFO) << fmt::format("spike fetch triggers ");
    proc.step(1);
    LOG(INFO) << fmt::format("Spike mcause={:08X}", state->mcause->read());
    return {};
  }
}

uint64_t VBridgeImpl::get_t() {
  return getCycle();
}

uint8_t VBridgeImpl::load(uint64_t address) {
  return *sim.addr_to_mem(address);
}

void VBridgeImpl::timeoutCheck() {
  if (get_t() > 1000) {
    LOG(FATAL_S) << fmt::format("Simulation timeout, t={}", get_t());
  }
}

void VBridgeImpl::dpiInitCosim() {
  google::InitGoogleLogging("emulator");
  FLAGS_logtostderr = true;

  ctx = Verilated::threadContextp();

  init_spike();

  LOG(INFO) << fmt::format("[{}] dpiInitCosim", getCycle());

  dpiDumpWave();
}



void VBridgeImpl::dpiPeekTL(const TlPeekInterface &tl_peek) {
  VLOG(3) << fmt::format("[{}] dpiPeekTL", get_t());
//  LOG(INFO) << fmt::format("DpiTLPeek address ={:08X}, valid = {}", tl_peek.a_bits_address, tl_peek.a_valid);

}

void VBridgeImpl::dpiPokeTL(const TlPokeInterface &tl_poke) {
  VLOG(3) << fmt::format("[{}] dpiPokeTL", get_t());
  *tl_poke.d_valid = 0;
  *tl_poke.d_bits_param = 0;
  *tl_poke.d_bits_size = 0;
  *tl_poke.d_bits_source = 0;
  *tl_poke.d_bits_sink = 0;
  *tl_poke.d_bits_denied = 0;
  *tl_poke.d_bits_data = 0;
  *tl_poke.d_corrupt = 0;
  *tl_poke.d_valid = 0;
  *tl_poke.a_ready = true;
}

void VBridgeImpl::dpiRefillQueue() {
  if(to_rtl_queue.empty()) loop_until_se_queue_full();
}

void VBridgeImpl::dpiCommitPeek(CommitPeekInterface cmInterface) {

    uint64_t pc = cmInterface.wb_reg_pc;
    LOG(INFO) << fmt::format("RTL write back insn {:08X} ", pc);
    // Check rf write
    // todo: use rf_valid
    if (cmInterface.rf_wen) {
      record_rf_access(cmInterface);
    }
    // commit spike event
    bool commit = false;

    for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
      if (se_iter->pc == pc) {
        // mechanism to the insn which causes trap.
        // trapped insn will commit with the first insn after trap(0x80000004).
        // It demands the trap insn not to be the last one in the queue.
        if (se_iter->pc == 0x80000004) {
          for (auto se_it = to_rtl_queue.rbegin(); se_it != to_rtl_queue.rend(); se_it++) {
            if (se_it->is_trap) se_it->is_committed = true;
          }
        }
        commit = true;
        se_iter->is_committed = true;
        LOG(INFO) << fmt::format("Set spike {:08X} as committed", se_iter->pc);
        break;
      }
    }
    if (!commit) LOG(INFO) << fmt::format("RTL wb without se in pc =  {:08X}", pc);
    // pop the committed Event from the queue
    for (int i = 0; i < to_rtl_queue_size; i++) {
      if (to_rtl_queue.back().is_committed) {
        LOG(INFO) << fmt::format("Pop SE pc = {:08X} ", to_rtl_queue.back().pc);
        to_rtl_queue.pop_back();
      }
    }


}

void VBridgeImpl::record_rf_access(CommitPeekInterface cmInterface) {
    // peek rtl rf access
    uint32_t waddr = cmInterface.rf_waddr;
    uint64_t wdata = cmInterface.rf_wdata;
    uint64_t pc = cmInterface.wb_reg_pc;
    uint64_t insn = cmInterface.wb_reg_inst;

    uint8_t opcode = clip(insn, 0, 6);
    bool rtl_csr = opcode == 0b1110011;

    // exclude those rtl reg_write from csr insn
    if (!rtl_csr) {
      LOG(INFO) << fmt::format("RTL wirte reg({}) = {:08X}, pc = {:08X}", waddr, wdata, pc);
      // find corresponding spike event
      SpikeEvent *se = nullptr;
      for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
        if ((se_iter->pc == pc) && (se_iter->rd_idx == waddr) && (!se_iter->is_committed)) {
          se = &(*se_iter);
          break;
        }
      }
      if (se == nullptr) {
        for (auto se_iter = to_rtl_queue.rbegin(); se_iter != to_rtl_queue.rend(); se_iter++) {
          // LOG(INFO) << fmt::format("se pc = {:08X}, rd_idx = {:08X}",se_iter->pc,se_iter->rd_idx);
          LOG(INFO) << fmt::format("List: spike pc = {:08X}, write reg({}) from {:08x} to {:08X}, is commit:{}",
                                   se_iter->pc, se_iter->rd_idx, se_iter->rd_old_bits, se_iter->rd_new_bits, se_iter->is_committed);
        }
        LOG(FATAL) << fmt::format("RTL rf_write Cannot find se ; pc = {:08X} , insn={:08X}, waddr={:08X}", pc, insn, waddr);
      }
      // start to check RTL rf_write with spike event
      // for non-store ins. check rf write
      // todo: why exclude store insn? store insn shouldn't write regfile.
      if (!(se->is_store)) {
        CHECK_EQ_S(wdata, se->rd_new_bits) << fmt::format("\n RTL write Reg({})={:08X} but Spike write={:08X}", waddr, wdata, se->rd_new_bits);
      } else {
        LOG(INFO) << fmt::format("Find Store insn");
      }
    } else {
      LOG(INFO) << fmt::format("RTL csr insn wirte reg({}) = {:08X}, pc = {:08X}", waddr, wdata, pc);
    }

}

VBridgeImpl vbridge_impl_instance;




