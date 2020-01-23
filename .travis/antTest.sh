#!/usr/bin/env bash

# A helper script for TravisCI builds that saves the std
# out and err streams in a log file. This is needed
# because otherwise TravisCI complains that there is too
# much logging on stdout

ant -q test 2>&1 > ant-test.log
