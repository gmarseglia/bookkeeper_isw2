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
mvn test -Pjacoco,ignoreTestFailure,customTest
mvn test -Pbadua,ignoreTestFailure,customTest
if $PITEST
then
	mvn test -Ppitest,ignoreTestFailure,customTest
fi

cd results/ || exit
./saveCurrentResult.sh "$1" -f
