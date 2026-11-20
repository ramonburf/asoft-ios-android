# iOS 27 (experimental): the kernel its boots after the first use, fw/bootkc with the iOS 27 userland's patches
# (kc-ios27.py: kc-watch.py's eight, on top of kc-gles.py's prelink plist), as fw/bootkc27
progress 10 "the iOS 27 kernel"
python3 -I $A/phone/kc-ios27.py fw/bootkc fw/bootkc27
progress 100 "the iOS 27 kernel"
