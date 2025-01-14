#!/usr/bin/env bash

if [ "${flag}" == "onlySuccess" ] ; then
  TARGET_DIR="ba-dua-only-success"

else
  TARGET_DIR="ba-dua"
fi

mkdir -p target/site/$TARGET_DIR
cp target/badua.xml target/site/$TARGET_DIR
xmllint --format target/site/$TARGET_DIR/badua.xml > target/site/$TARGET_DIR/badua_pretty.xml