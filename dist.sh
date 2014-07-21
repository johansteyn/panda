#!/bin/bash

# This script will build the package (JAR) with Maven and
# zip it up with images and music files into a distribution

function remove {
	for i in $*
	do
		if [ -e $i ]
		then
			rm $i;
		fi
	done
}

function check {
	if [ $? != 0 ]
	then
		exit 1
	fi
}

export VERSION=1.0

echo "Removing old files..."
remove "panda-$VERSION.zip"

echo "Building with Maven..."
mvn clean package
check

echo "Zipping everything up..."
mkdir panda
cp target/panda-$VERSION.jar panda
cp panda.properties panda.tags panda.playlists panda
cp -r images panda
cp -r music panda
zip -r --quiet panda-$VERSION.zip panda
check

echo "Cleaning up..."
rm -rf panda

echo "Done."

