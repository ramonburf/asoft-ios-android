"""kc-ios27.py IN OUT: the iOS 27 kernel, cloudOS 26.4b's research kernel (fw/bootkc: the stock one, or kc-gles.py's,
which only grows the prelink plist) with the iOS 27 userland's patches, the ones kc-watch.py gives watchOS 27:

  - IOMobileFramebuffer user client: checkStructureInputSize 0x588 -> any, SwapEnd's cmp w2,#0x588 -> #0x6e0
    (27.x sends 0x6e0 bytes: without it no swap reaches the scanout, so no picture)
  - container_manager upcall (JB-02d): the cbz after the upcall becomes a b to success (without it every daemon
    with a container or a sandbox profile that containermanagerd answers differently on 27.x failed its exec:
    "xpcproxy exited due to OS_REASON_EXEC", code 8, security policy; backboardd among them)
  - shared region 0x1C0000000 at 0x180000000, for 27.x's dyld cache
  - "IOUC %s failed sandbox" goes to the allow path
  - Sandbox's mpo_proc_check_syscall_unix -> 0 (HFS+'s renameat fallback the 26.4 profiles deny)

Each one is checked against the stock bytes first; one already patched is left as it is. Offsets are file offsets
in the stock kernelcache, which kc-gles.py does not move (it edits only __PRELINK_INFO,__info).
"""
import sys

PATCHES = [   # file offset, stock bytes, patched bytes, what (kc-watch.py's)
    (0x09c7234, '88050000', 'ffffffff', 'IOMFB dispatch checkStructureInputSize'),
    (0x16ae22d, '2016', '801b', 'IOMFB SwapEnd size 0x588 -> 0x6e0'),
    (0x1a9b86c, 'f44fbea9fd7b01a9', '00008052ff0f5fd6', 'Sandbox mpo_proc_check_syscall_unix -> 0'),
    (0x1ab0654, '80f8', 'c4ff', 'container_manager upcall (1/2)'),
    (0x1ab0657, '34', '17', 'container_manager upcall (2/2)'),
    (0x1e0f4e8, 'f60761b2c00e084ee01f803d', '1600b8d23600c0f2fa0761b2', 'shared region size (1/2)'),
    (0x1e0f4f5, '0761b2', '5b07a9', 'shared region size (2/2)'),
    (0x24597b0, 'e01f40f9', 'beffff17', 'IOUC sandbox gate'),
]


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    k = bytearray(open(sys.argv[1], 'rb').read())
    for off, old, new, what in PATCHES:
        old, new = bytes.fromhex(old), bytes.fromhex(new)
        have = bytes(k[off:off + len(old)])
        if have == new:
            print(f'{off:#09x} {what}: patched already')
            continue
        if have != old:
            sys.exit(f'{off:#x} ({what}): {have.hex()}, not {old.hex()}: not cloudOS 26.4b\'s research kernel')
        k[off:off + len(new)] = new
        print(f'{off:#09x} {old.hex()} -> {new.hex()}  {what}')
    open(sys.argv[2], 'wb').write(k)
    print('wrote', sys.argv[2])


if __name__ == '__main__':
    main()
