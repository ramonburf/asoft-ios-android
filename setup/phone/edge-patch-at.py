"""edge-patch.py (asoft-ios ios26/scripts) for any build: the same cave, relinked at that build's addresses.

    edge-patch-at.py DSC_DIR OUT_DIR CAVE_VA CALL_VA ORIG_EDGE_TYPE GET_INTEGER GET_FLOAT

edge-cave.hex is edge-cave.s linked at 23G90's addresses. Only its five BL instructions depend on where it sits
(its literal loads are relative to the cave itself), so they are rewritten here for CAVE_VA and the build's
_UIEventHIDTouchEdgeTypeForHIDEvent (ORIG_EDGE_TYPE), IOHIDEventGetIntegerValue (GET_INTEGER) and
IOHIDEventGetFloatValue (GET_FLOAT). CALL_VA is the BL ORIG_EDGE_TYPE in _UITouchSetBasicTouchPropertiesFromEvent,
which is turned into a BL to the cave. The cave goes into zero padding that dsc-patch.py checks is zero, and the
call site's old bytes are checked too. Prints what edge-patch.py prints.
"""
import os, struct, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
S = os.environ.get('ASOFT_SCRIPTS') or os.path.join(os.environ.get('ASOFT26', ''), os.environ.get('ASOFT_S', 'scripts'))
# 23G90's link addresses of edge-cave.hex (edge-patch.py)
CAVE0, ORIG0, INT0, FLT0 = 0x18b66b640, 0x1896df8d4, 0x18d81b100, 0x18d81b0f0


def bl(at, to):
    d = (to - at) >> 2
    assert -(1 << 25) <= d < (1 << 25), 'BL out of range: %#x -> %#x' % (at, to)
    return struct.pack('<I', 0x94000000 | (d & 0x3ffffff))


def relink(cave, va, orig, gi, gf):
    b = bytearray(cave)
    moved = {ORIG0: orig, INT0: gi, FLT0: gf}
    n = 0
    for i in range(0, len(b), 4):
        w, = struct.unpack_from('<I', b, i)
        if w & 0xfc000000 != 0x94000000:
            continue
        off = w & 0x3ffffff
        off = off - (1 << 26) if off & (1 << 25) else off
        old_to = CAVE0 + i + (off << 2)
        if old_to not in moved:
            sys.exit('edge-cave.hex: a BL at +%#x to %#x, which is none of the three' % (i, old_to))
        b[i:i + 4] = bl(va + i, moved[old_to])
        n += 1
    if n != 5:
        sys.exit('edge-cave.hex: %d BLs, expected 5' % n)
    return bytes(b)


def run(src, va, old, new, out):
    r = subprocess.run([sys.executable, '-I', os.path.join(S, 'dsc-patch.py'), src, hex(va), old, new, out],
                       capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stdout + r.stderr)
    return r.stdout.split()


def main():
    if len(sys.argv) != 8:
        sys.exit(__doc__)
    src, out = sys.argv[1], sys.argv[2]
    cave_va, call_va, orig, gi, gf = (int(x, 0) for x in sys.argv[3:8])
    cave = relink(bytes.fromhex(open(os.path.join(S, 'edge-cave.hex')).read().strip()), cave_va, orig, gi, gf)
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=out) as tmp:
        run(src, cave_va, '00' * len(cave), cave.hex(), tmp)
        name, _, new = run(tmp, call_va, bl(call_va, orig).hex(), bl(call_va, cave_va).hex(), out)
    print('%s %s (add this CDHash to the trust cache)' % (name, new))


if __name__ == '__main__':
    main()
