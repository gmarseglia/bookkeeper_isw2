#!/bin/bash

if [[ $# -lt 1 ]]
then
	echo "Incorrect usage."
	exit 1
fi

PITEST=false

for arg in "$@"
do
	if [ "$arg" == "-p" ]
	then
		PITEST=true
	fi
done

cd ..
mvn clean
mvn test -Pjacoco
mvn test -Pbadua
if $PITEST
then
	mvn test -Ppitest
fi

cd results/ || exit
./results/saveCurrentResult.sh "$1" -f
