@echo off

echo Running tests that need to be inspected manually to ensure the worked.
echo:

if not defined TEST_DATA_ROOT (
    echo TEST_DATA_ROOT must be set for testing to work.
    exit /b 1
)

echo ..\..\bin\oliveenroll --plugin sid-embed-v5-py3  --domain multilang-v1   --enroll joshua  --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\joshua2.wav
call ..\..\bin\oliveenroll --plugin sid-embed-v5-py3  --domain multilang-v1   --enroll joshua  --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\joshua2.wav
echo:

echo ..\..\bin\oliveenroll --plugin sid-embed-v5-py3 --domain multilang-v1 --enroll joshua --%TEST_DATA_ROOT%\testSuite\sid\enroll\joshua2.wav
call ..\..\bin\oliveenroll --plugin sid-embed-v5-py3 --domain multilang-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\joshua2.wav
echo:

echo ..\..\bin\oliveenroll --plugin sdd-sbcEmbed-v1b-py3 --domain telClosetalk-v1 --enroll joshua --wav doc\joshua2.wav
call ..\..\bin\oliveenroll --plugin sdd-sbcEmbed-v1b-py3 --domain telClosetalk-v1 --enroll joshua --wav doc\joshua2.wav
echo:

echo ..\..\bin\oliveenroll --plugin tpd-embed-v1-py3 --domain eng-cts-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testdata\TaadA_1min.wav
call ..\..\bin\oliveenroll --plugin tpd-embed-v1-py3 --domain eng-cts-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testdata\TaadA_1min.wav
echo:

echo ..\..\bin\oliveenroll --plugin tpd-embed-v1-py3 --domain rus-cts-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testdata\TaadA_1min.wav
call ..\..\bin\oliveenroll --plugin tpd-embed-v1-py3 --domain rus-cts-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testdata\TaadA_1min.wav
echo:

echo ..\..\bin\oliveenroll --plugin qbe-tdnn-v7-py3 --domain digPtt-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\short1.wav
call ..\..\bin\oliveenroll --plugin qbe-tdnn-v7-py3 --domain digPtt-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\joshuashort1.wav
echo:

echo ..\..\bin\oliveenroll --plugin qbe-tdnn-v7-py3 --domain multi-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\short1.wav
call ..\..\bin\oliveenroll --plugin qbe-tdnn-v7-py3 --domain multi-v1 --enroll joshua --wav %TEST_DATA_ROOT%\testSuite\sid\enroll\joshuashort1.wav
echo: