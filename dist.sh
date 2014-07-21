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

echo "Removing old zip files..."
remove "panda-$VERSION.zip"
remove "music.zip"

echo "Building with Maven..."
mvn clean package
check

echo "Zipping up built artifacts and config files..."
mkdir panda
cp target/panda-$VERSION.jar panda
cp panda.properties panda.tags panda.playlists panda
cp -r images panda
zip -r --quiet panda-$VERSION.zip panda
check

echo "Zipping up sample music files..."
zip -r --quiet music.zip music
check

echo "Cleaning up..."
rm -rf panda

echo "Done."

