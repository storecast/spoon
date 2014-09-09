#!/bin/sh
cd ~/source/dev
for path in $(find api common server -type d -regex '.*contentprovider/src/main/java$' -printf '%p ')
do
	echo $path
	echo ~/source/lts/$path
        java -jar ~/source/spoon/target/spoon-core-3.0-SNAPSHOT-jar-with-dependencies.jar -i $path -o ~/source/lts/$path -p com.txtr.test.UpdateInterface --fragments --lines --with-imports

done