#!/usr/bin/env python3

from constants import *
from parse import *

if __name__ == "__main__":
    tpe = sys.argv[1]
    if tpe in ["rv64*", "rv32*", "rv_*"]:
        match tpe:
            case "rv64*":
                obj_name = "Instructions64"
            case "rv32*":
                obj_name = "Instructions32"
            case "rv_*":
                obj_name = "Instructions"
        instrs = create_inst_dict([tpe], False)
        instrs_str = ("package org.chipsalliance.rocket\n"
                      "import chisel3.util.BitPat\n"
                      f"object {obj_name} {{\n")
        for i in instrs:
            instrs_str += f'  def {i.upper().replace(".","_")} = BitPat("b{instrs[i]["encoding"].replace("-","?")}")\n'
        instrs_str += "}"
        print(instrs_str)
    if tpe in ["causes"]:
        cause_names_str = ("package org.chipsalliance.rocket\n"
                           "object Causes {\n")
        for num, name in causes:
            cause_names_str += f'  val {name.lower().replace(" ","_")} = {hex(num)}\n'
        cause_names_str += '''  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
'''
        for num, name in causes:
            cause_names_str += f'    res += {name.lower().replace(" ","_")}\n'
        cause_names_str += '''    res.toArray
  }'''
        cause_names_str += "}"
        print(cause_names_str)
    if tpe in ["csrs"]:
        csr_names_str = ("package org.chipsalliance.rocket\n"
                         "object CSRs {\n")
        for num, name in csrs+csrs32:
            csr_names_str += f'  val {name} = {hex(num)}\n'
        csr_names_str += '''  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
'''
        for num, name in csrs:
            csr_names_str += f'''    res += {name}\n'''
        csr_names_str += '''    res.toArray
      }
  val all32 = {
    val res = collection.mutable.ArrayBuffer(all:_*)
'''
        for num, name in csrs32:
            csr_names_str += f'''    res += {name}\n'''
        csr_names_str += '''    res.toArray
  }
'''
        csr_names_str += "}"
        print(csr_names_str)
