# Another build than 23G90 (iOS 27), before its first boot: its dyld cache's header lets dyld slide the cache by up
# to 512 MB, but the cloudOS 26.4 kernel's shared region is 6 GB and the cache fills 5.96 GB of it, so the kernel
# refused the mapping and launchd stopped at "Library not loaded: /usr/lib/libSystem.B.dylib". The header's
# maxSlide (dscPatches.maxSlide) becomes what fits; the main cache file is re-attested by dsc-patch.py and its new
# CDHash joins root26.tc.
progress 5 "the dyld cache's slide"
mkdir -p install/work
rm -rf msin msout
in_root root26g.qcow2 <<'EOF'
C=$R/private/preboot/Cryptexes/OS/System/Library/Caches/com.apple.dyld
mkdir -p $A/msin
cp $C/dyld_shared_cache_arm64e $A/msin/
python3 -I $S/dsc-patch.py $A/msin {MAXSLIDE} $A/msout > $A/install/work/maxslide.out
cat $A/msout/dyld_shared_cache_arm64e > $C/dyld_shared_cache_arm64e
EOF
cat install/work/maxslide.out
set -- $(cat install/work/maxslide.out)
[ "${#3}" = 40 ] || { echo "dsc-patch.py printed no new CDHash"; exit 1; }
echo "$3" > install/work/maxslide.hashes
python3 -I $S/mktc.py -2 root26-ms.tc root26.tc install/work/maxslide.hashes
mv root26-ms.tc root26.tc
rm -rf msin msout
progress 100 "the dyld cache's slide"
