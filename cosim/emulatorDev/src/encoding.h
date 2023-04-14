#pragma once

// Write this CSR to end simulation.
constexpr uint32_t CSR_MSIMEND = 0x7cc;

namespace TlOpcode {
  constexpr int
      AcquireBlock = 6,
      Get = 4,
      AccessAckData = 1,
      PutFullData = 0,
      PutPartialData = 1,
      AccessAck = 4;
}

struct TlPeekInterface {
  svBitVecVal a_bits_opcode;
  svBitVecVal a_bits_param;
  svBitVecVal a_bits_size;
  svBitVecVal a_bits_source;
  svBitVecVal a_bits_address;
  svBitVecVal a_bits_mask;
  svBitVecVal a_bits_data;
  svBit a_corrupt;
  svBit a_valid;
  svBit d_ready;
};

struct TlPeekStatusInterface {
    svBitVecVal pc;
    svBit s2_miss;
};

struct TlPokeInterface {
  svBitVecVal *d_bits_opcode;
  svBitVecVal *d_bits_param;
  svBitVecVal *d_bits_size;
  svBitVecVal *d_bits_source;
  svBitVecVal *d_bits_sink;
  svBitVecVal *d_bits_denied;
  svBitVecVal *d_bits_data;
  svBit *d_corrupt;
  svBit *d_valid;

  svBit d_ready;
};


struct CommitPeekInterface {
  svBit rf_wen;
  svBit wb_valid;
  svBitVecVal rf_waddr;
  svBitVecVal rf_wdata;
  svBitVecVal wb_reg_pc;
  svBitVecVal wb_reg_inst;
};


