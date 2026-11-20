"""hact_patch.py (asoft-ios ios26/scripts) for any build: bytes at a file offset, the old ones checked first.

    patch-at.py ORIGINAL OUT OFFSET OLDHEX NEWHEX

For mobileactivationd's -[DeviceType should_hactivate] the manifest's hact entry gives the build's offset of the
getter `ldrb w0, [x0, #N] ; ret` and `mov x0, #1 ; ret` for it.
"""
import sys

if len(sys.argv) != 6:
    sys.exit(__doc__)
src, out, off, old, new = sys.argv[1], sys.argv[2], int(sys.argv[3], 0), bytes.fromhex(sys.argv[4]), bytes.fromhex(sys.argv[5])
if len(old) != len(new):
    sys.exit('old and new bytes differ in length')
d = bytearray(open(src, 'rb').read())
if d[off:off + len(old)] != old:
    sys.exit(f'unexpected bytes at {off:#x}: {d[off:off + len(old)].hex()}, expected {old.hex()}')
d[off:off + len(new)] = new
open(out, 'wb').write(d)
print(f'{out}: {old.hex()} -> {new.hex()} at {off:#x}')
