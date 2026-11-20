# Another build than 23G90 (iOS 27), before its first boot: dyld shared cache patches the cloudOS 26.4 kernel needs
# (the manifest's dscPatches.maxSlide and dscPatches.preboot, {PREBOOT}: "VA OLD NEW" entries joined by ';').
#  - maxSlide: the cache's header lets dyld slide it by up to 512 MB, but this kernel's shared region is 6 GB and the
#    cache fills 5.96 GB of it, so the kernel refused the mapping ("Library not loaded: /usr/lib/libSystem.B.dylib").
#    Its OLD is "-": whatever the header holds (a disk patched before has another value).
#  - preboot: code that asks this kernel for what only iOS 27's has (libsystem_darwin's Lockdown Mode sysctl,
#    security.mac.lockdown_mode_state_public: launchd aborted at its first spawn).
# Each one goes into its subcache on the disk (dsc-patch.py re-attests it) and its new CDHash joins root26.tc; one
# whose bytes are NEW already is skipped.
progress 5 "the dyld cache's patches"
mkdir -p install/work
echo "{PREBOOT}" | tr ';' '\n' | sed '/^ *$/d' > install/work/preboot.list
cat install/work/preboot.list
: > install/work/preboot.hashes
in_root root26g.qcow2 <<'EOF'
C=$R/private/preboot/Cryptexes/OS/System/Library/Caches/com.apple.dyld
while read va old new; do
	set -- $(python3 -I $A/phone/dsc-which.py $C $va $(( ${#new} / 2 )))
	f=$1 cur=$2
	if [ "$cur" = "$new" ]; then echo "$f $va: $new already"; continue; fi
	[ "$old" = "-" ] && old=$cur
	rm -rf $A/msin $A/msout
	mkdir -p $A/msin
	cp $C/$f $A/msin/
	python3 -I $S/dsc-patch.py $A/msin $va $old $new $A/msout | tee -a $A/install/work/preboot.out
	set -- $(tail -1 $A/install/work/preboot.out)
	[ "${#3}" = 40 ] || { echo "dsc-patch.py printed no new CDHash for $va"; exit 1; }
	echo "$3" >> $A/install/work/preboot.hashes
	cat $A/msout/$f > $C/$f
	rm -rf $A/msin $A/msout
done < $A/install/work/preboot.list
EOF
if [ -s install/work/preboot.hashes ]; then
	python3 -I $S/mktc.py -2 root26-ms.tc root26.tc install/work/preboot.hashes
	mv root26-ms.tc root26.tc
fi
progress 100 "the dyld cache's patches"
