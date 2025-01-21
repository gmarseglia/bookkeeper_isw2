#!/bin/bash

if [[ $# -lt 2 ]]
then
	echo "Incorrect usage. Correct usage: runAndSave.sh <version_name> <--rc or --bf or --all> [-p]"
	exit 1
fi

PITEST=false
READ_CACHE=false
BUFFERED_CHANNEL=false
ALL=false
ACTIVE_PROFILE=""

for arg in "$@"
do
	if [ "$arg" == "-p" ] ; then
		PITEST=true
	fi
	if [ "$arg" == "--rc" ] ; then
    READ_CACHE=true
    ACTIVE_PROFILE="MyReadCacheSuite"
  fi
  if [ "$arg" == "--bf" ] ; then
    BUFFERED_CHANNEL=true
    ACTIVE_PROFILE="MyBufferedChannelSuite"
  fi
  if [ "$arg" == "--all" ] ; then
    ALL=true
    ACTIVE_PROFILE="MyAllSuite"
  fi
done

if [ $READ_CACHE == false ] && [ $BUFFERED_CHANNEL == false ] && [ $ALL == false ] ; then
  echo "Incorrect usage. Correct usage: runAndSave.sh <version_name> <--rc or --bf or --all> [-p]"
  exit 1
fi

cd ..
mvn clean
mvn test -Pjacoco,ignoreTestFailure,customTest,$ACTIVE_PROFILE
mvn test -Pbadua,ignoreTestFailure,customTest,$ACTIVE_PROFILE
if $PITEST
then
	mvn test -Ppitest,ignoreTestFailure,customTest,$ACTIVE_PROFILE
fi

cd results/ || exit
./saveCurrentResult.sh "$1" -f
