#!/bin/sh
# After editing icons in inkscape, run this to map
# standard colours to known android theme names
DRAWABLE=src/main/res/drawable
for f in $DRAWABLE/ic_action*.xml; do
	cat $f | \
		sed -e '/android:strokeColor="#......00"/d' | \
		sed -e '/android:fillColor="#......00"/d' | \
		sed -e 's/"#8c8c8c"/"#890000"/g' | \
		sed -e 's/"#00dac8"/"#FFFFFF"/g' | \
		sed -e 's/"#d8d8d8"/"#FFFFFF"/g' | \
		sed -e 's/"#f.f.f."/"#FAFAFA"/g' > /tmp/xml
	mv /tmp/xml $f
done
# Check recolouring is done
grep '"#' $DRAWABLE/ic_action_*.xml
