#!/usr/bin/env python3
"""Extract complete pinned Rust owner/lifecycle guards, never source-level guesses."""
from pathlib import Path
import argparse, hashlib, importlib.util, json, struct

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import hyperceiler_test_paths as test_paths

ROOT = test_paths.TEST_DATA_ROOT
sha = lambda b: hashlib.sha256(b).hexdigest()
class Elf:
 def __init__(self,path,expected):
  self.path=Path(path);self.data=self.path.read_bytes();assert sha(self.data)==expected,(path,'SHA mismatch')
  assert self.data[:6]==b'\x7fELF\x02\x01' and struct.unpack_from('<H',self.data,18)[0]==183
  off=struct.unpack_from('<Q',self.data,32)[0];size,n=struct.unpack_from('<HH',self.data,54);assert size==56
  self.phend=off+size*n;self.ph=[struct.unpack_from('<IIQQQQQQ',self.data,off+i*size)for i in range(n)];self.span=max(x[3]+x[6]for x in self.ph if x[0]==1)
  off=struct.unpack_from('<Q',self.data,40)[0];size,n,names=struct.unpack_from('<HHH',self.data,58);assert size==64
  sections=[struct.unpack_from('<IIQQQQIIQQ',self.data,off+i*size)for i in range(n)];ns=sections[names];strings=self.data[ns[4]:ns[4]+ns[5]];self.sections={strings[s[0]:strings.index(0,s[0])].decode():s for s in sections}
  strings=self.section('.dynstr');self.symbols=[(strings[n:strings.index(0,n)].decode(),value,size,info,index)for n,info,other,index,value,size in struct.iter_unpack('<IBBHQQ',self.section('.dynsym'))]
 def section(self,n):
  s=self.sections[n];return self.data[s[4]:s[4]+s[5]]
 def read(self,va,size,immutable=True):
  found=[s for s in self.ph if s[0]==1 and s[3]<=va and va+size<=s[3]+s[5]];assert len(found)==1,(self.path,hex(va),size)
  p=found[0];assert not immutable or not(p[1]&2),(self.path,hex(va),'mutable guard')
  start=p[2]+va-p[3];return self.data[start:start+size]
 def relocations(self):
  if hasattr(self,'_relocations'):return self._relocations
  out=[]
  for n in ['.rela.dyn','.rela.plt']:
   if n not in self.sections:continue
   data=self.section(n)
   if data.startswith(b'APS2'):
    pos=4
    def read():
     nonlocal pos
     result=0;shift=0
     while True:
      assert pos<len(data) and shift<70
      b=data[pos];pos+=1;result|=(b&127)<<shift;shift+=7
      if not b&128:return result-(1<<shift)if b&64 else result
    count,offset=read(),read();addend=0;rows=[]
    while len(rows)<count:
     group,flags=read(),read();assert group>0 and len(rows)+group<=count and flags&~15==0
     delta=read()if flags&2 else None;info=read()if flags&1 else None
     kind=flags&12
     if kind==12:addend+=read()
     elif kind!=8:addend=0
     for _ in range(group):
      offset+=delta if flags&2 else read()
      if not flags&1:info=read()
      if kind==8:addend+=read()
      rows.append((offset,info,addend))
    assert pos==len(data)
   else:
    assert self.sections[n][1]==4
    rows=struct.iter_unpack('<QQq',data)
   for slot,info,addend in rows:out.append({'slot':slot,'type':info&0xffffffff,'symbol':self.symbols[info>>32][0],'addend':addend})
  self._relocations=out
  return out

HEADER = test_paths.SOURCE_ROOT / 'app/src/main/cpp/targets/home/recents_guard.generated.h'
REPORT = test_paths.TEST_DATA_ROOT / 'acceptance/home-recents-next/post-test8-v1/static-final-v4/report.json'
ELF = test_paths.ROM_ROOT / 'launcher-native/lib/arm64-v8a/libapp_launcher.so'
SHA = '4dc9fa82371dda8a0a8b9eff48301469d2f84752b52b71635b84e5fc0c47fa6e'

def generate():
    e = Elf(ELF, SHA)
    report = json.loads(REPORT.read_text())
    assert report['passed'] and report['inputs_unchanged']
    regions = []
    relocations = {r['slot']: r for r in e.relocations()}
    def add(name, va, size, expected=None):
        data = e.read(va, size)
        assert expected is None or sha(data) == expected, name
        assert not any(va <= slot < va + size for slot in relocations), name
        regions.append(dict(name=name, va=va, size=size, sha256=sha(data), data=data))
    add('ELF and complete program headers', 0, e.phend)
    for va, r in report['functions']['rust'].items():
        add(r['name'], int(va, 0), r['size'], r['body_sha256'])
    # Complete concrete Once implementation and its outlined argument shims.
    # Function sizes are independently read from the ELF's debug symbol table.
    symbol_reader_path = test_paths.SOURCE_ROOT / 'tests/home-layout-native/fixture_symbols.py'
    sym_spec = importlib.util.spec_from_file_location('recents_symbols', symbol_reader_path)
    sym_reader = importlib.util.module_from_spec(sym_spec); sym_spec.loader.exec_module(sym_reader)
    symbols = {va:(size,name) for va,size,name in sym_reader.symbols(ELF)}
    for va,size in [(0xd8e0fc,708),(0xdd6628,12),(0xdd64f0,16),(0xdd63d4,20)]:
        assert symbols[va][0] == size
        add(symbols[va][1],va,size)
    add('original setting name', 0x20739a, 35, sha(b'transition_animation_duration_ratio'))
    add('original simple-mode factor', 0x180cb8, 4, sha(struct.pack('<f', .8)))
    # .init_array is relocated, so compare resolved pointers separately. Each
    # exact inventory constructor body is bound too; this is not a claim about
    # arbitrary dependency constructors or another module's native callbacks.
    init = e.sections['.init_array']; assert init[3] == 0xf09920 and init[5] == 53 * 8, init
    ptrs = {}
    for slot in range(init[3], init[3] + init[5], 8):
        r = relocations[slot]; assert r['type'] == 1027
        ptrs[slot] = r['addend']
    assert len(ptrs) == 53
    existing = {r['va'] for r in regions}
    for target in sorted(set(ptrs.values())):
        if target not in existing: add('inventory constructor', target, 20)
    for target in (0x9783dc, 0x978424): add('inventory registration helper', target, 72)
    for slot, target in [(0xe1e518, 0x5e04b4), (0xe1e568, 0x5e05bc),
                         (0xe13838, 0x73cf1c), (0xe06e18, 0x68de38), (0xe06e20, 0x625708)]:
        r = relocations[slot]; assert r['type'] == 1027 and r['addend'] == target, (slot, r)
        ptrs[slot] = target
    # Bind every pointer in the actual Once closure table, not just its call.
    for slot in range(0xe06e00, 0xe06e28, 8):
        if slot in relocations:
            r = relocations[slot]; assert r['type'] == 1027; ptrs[slot] = r['addend']
        else:
            raw = e.read(slot, 8, immutable=False)
            regions.append(dict(name='Once vtable scalar', va=slot, size=8, sha256=sha(raw), data=raw))
    constants = dict(kImageSpan=e.span, kEntry=0x9b544c, kPatch=0x7a58a8,
                     kResume=0x7a58ac, kOwner=0xf19db0, kOnce=0xf19dc0)
    assert struct.unpack('<I', e.read(constants['kPatch'], 4))[0] == 0xbd001fa0
    text = ['/* SPDX-License-Identifier: AGPL-3.0-or-later */',
            '// Generated by tests/home-recents-os4/generate.py from the exact ROM ELF.',
            '#pragma once', '#include <array>', '#include <cstdint>', '#include <span>',
            'namespace home_recents_guard {',
            'struct Guard {uintptr_t va; std::span<const uint8_t> bytes;};',
            'struct Pointer {uintptr_t slot, target;};',
            f'inline constexpr char kSourceSha256[]="{SHA}";',
            'inline constexpr uint32_t kOriginalWord=0xbd001fa0;']
    for name, value in constants.items(): text.append(f'inline constexpr uintptr_t {name}=0x{value:x};')
    for i, r in enumerate(regions):
        text += [f'// RVA0x{r["va"]:x}; {r["size"]} bytes; {r["sha256"]}',
                 f'inline constexpr std::array<uint8_t,{r["size"]}> kBytes{i}={{']
        text += ['    ' + ','.join(f'0x{x:02x}' for x in r['data'][j:j+16]) + ',' for j in range(0, r['size'], 16)]
        text += ['};']
    text += [f'inline constexpr std::array<Guard,{len(regions)}> kGuards={{{{']
    text += [f'    {{0x{r["va"]:x},kBytes{i}}},' for i,r in enumerate(regions)]
    text += ['}};', f'inline constexpr std::array<Pointer,{len(ptrs)}> kPointers={{{{']
    text += [f'    {{0x{s:x},0x{t:x}}},' for s,t in sorted(ptrs.items())]
    text += ['}};', '} // namespace home_recents_guard', '']
    manifest = dict(inputs={str(p):sha(p.read_bytes()) for p in (ELF, REPORT, Path(__file__), symbol_reader_path)},
                    guards=[{k:v for k,v in r.items() if k != 'data'} for r in regions],
                    pointers=[dict(slot=s,target=t) for s,t in sorted(ptrs.items())], constants=constants,
                    cold_observation={'once':3,'owner':0,'observer':0,'reservation':False}, device_status='NOT_TESTED')
    return '\n'.join(text), manifest

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--output',type=Path,required=True)
    p.add_argument('--check',action='store_true'); a=p.parse_args()
    text, report=generate(); assert generate()[0]==text
    a.output.mkdir(parents=True,exist_ok=False)
    if a.check: assert HEADER.read_text()==text
    else: HEADER.write_text(text)
    report.update(passed=True, header={'path':str(HEADER),'sha256':sha(HEADER.read_bytes())},
                  generator={'path':str(Path(__file__).resolve()),'sha256':sha(Path(__file__).read_bytes())})
    (a.output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'passed':True,'guards':len(report['guards']),'pointers':len(report['pointers'])}))
if __name__=='__main__': main()
