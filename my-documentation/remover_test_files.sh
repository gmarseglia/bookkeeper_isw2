find . -type f -wholename "*/test/*.java" >> my-documentation/removed_test_files.txt
find . -type f -wholename "*/test/*.java" -exec git rm {} \;

