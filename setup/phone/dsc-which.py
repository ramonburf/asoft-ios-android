"""dsc-which.py DSC_DIR VA LEN: the subcache of a dyld shared cache whose mappings hold VA, and the LEN bytes there.

Prints "NAME HEX" (dsc-patch.py's lookup, without reading whole files), or exits 1 when no subcache maps VA.
"""
import os
import struct
import sys


def main():
    src, va, n = sys.argv[1], int(sys.argv[2], 0), int(sys.argv[3], 0)
    for name in sorted(os.listdir(src)):
        path = os.path.join(src, name)
        with open(path, 'rb') as f:
            head = f.read(0x4000)
            if not head.startswith(b'dyld_v1'):
                continue
            moff, mcount = struct.unpack_from('<II', head, 0x10)
            for i in range(mcount):
                addr, size, foff = struct.unpack_from('<QQQ', head, moff + 32 * i)
                if addr <= va < addr + size:
                    f.seek(foff + va - addr)
                    print(name, f.read(n).hex())
                    return
    sys.exit('%#x is in no subcache of %s' % (va, src))


if __name__ == '__main__':
    main()
