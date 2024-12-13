#!/bin/bash

if [[ $# -lt 1 ]]
then
	echo "Incorrect usage. Correct usage: <version>."
	exit 1
fi

VERSION=$1
OUT_DIR="$VERSION/"

if [[ -d "$OUT_DIR" ]]
then
	echo "Version: $VERSION already exists."
	FORCE_DELETE=false
	for arg in "$@"; do
    		if [ "$arg" == "-f" ]
		then
			FORCE_DELETE=true
        	fi
	done

	if $FORCE_DELETE
	then
		rm -rf "$OUT_DIR"
		echo "Version: $VERSION deleted."
        else
		exit 1
	fi
fi

echo "Version: $VERSION created successfully."

for arg in "$@"; do
    if [ "$arg" == "-f" ]; then
        rm -rf "$OUT_DIR"
        break
    fi
done
cp -r ../target/site "$OUT_DIR"

