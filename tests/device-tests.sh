#!/usr/bin/env bash
gradle --no-daemon :app:connectedDebugAndroidTest
test_result=$?
adb exec-out run-as com.hamyar360.app cat files/ocr-test.png > app/build/ocr-test.png
exit "$test_result"
