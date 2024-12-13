#!/usr/bin/env bash

mkdir -p target/site/ba-dua
cp target/badua.xml target/site/ba-dua
xmllint --format target/site/ba-dua/badua.xml > target/site/ba-dua/badua_pretty.xml