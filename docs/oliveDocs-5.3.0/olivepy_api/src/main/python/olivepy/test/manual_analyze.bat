@echo off

: Note:
:   * I go out of my way to try different cobinations of full, partial, and single
:     letter flags.
:   * The non-standard formatting makes it obvious that the echo and the real command are 
:     character for character identical.

echo Running tests on olive_analyze.py that need to be inspected manually to ensure they worked.
echo:

if not defined TEST_DATA_ROOT (
    echo TEST_DATA_ROOT must be set for testing to work.
    exit /b 1
)

echo ..\..\bin\oliveanalyze  --plugin enh-mmse-v1 --domain multi-v1 --enhance  -w %TEST_DATA_ROOT%\testSuite\sid\test\BI_1038-0000_M_Sm_Ara_S2.wav
call ..\..\bin\oliveanalyze  --plugin enh-mmse-v1 --domain multi-v1 --enhance  -w %TEST_DATA_ROOT%\testSuite\sid\test\BI_1038-0000_M_Sm_Ara_S2.wav
echo:

echo ..\..\bin\oliveanalyze  --plugin sad-dnn-v6b-py3 --domain multi-v1 --frame  -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  --plugin sad-dnn-v6b-py3 --domain multi-v1 --frame  -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze  --plugin sad-dnn-v6b-py3 --domain multi-v1 --reg  -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\olivanalyze  --plugin sad-dnn-v6b-py3 --domain multi-v1 --reg  -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze  -p lid-embedplda-v1b-py3 -d multi-v1 --global -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  -p lid-embedplda-v1b-py3 -d multi-v1 --global -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze  -p lid-embedplda-v1b-py3 -d multi-v1 -g -w doc\sw2269A-ms98-a-0017.wav
call ..\..\bin\oliveanalyze  -p lid-embedplda-v1b-py3 -d multi-v1 -g -w doc\sw2269A-ms98-a-0017.wav
echo:

echo ..\..\bin\oliveanalyze  --pl sdd-sbcEmbed-v1b-py3 --do micFarfield-v1 -r --wa %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  --pl sdd-sbcEmbed-v1b-py3 --do micFarfield-v1 -r --wa %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze  --plug sdd-sbcEmbed-v1b-py3 --doma telClosetalk-v1  --regi --wav %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  --plug sdd-sbcEmbed-v1b-py3 --doma telClosetalk-v1  --regi --wav %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo: 

echo ..\..\bin\oliveanalyze  -p sid-dplda-v1-py3 -d multi-v1 -g -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  -p sid-dplda-v1-py3 -d multi-v1 -g -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze --plugin sid-embed-v5-py3 --domain multicond-v1 --global --wav %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze --plugin sid-embed-v5-py3 --domain multicond-v1 --global --wav %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo:

echo ..\..\bin\oliveanalyze -r -d eng-cts-v1 -p tpd-embed-v1-py3 -w doc\sw2269A-ms98-a-0017.wav
call ..\..\bin\oliveanalyze -r -d eng-cts-v1 -p tpd-embed-v1-py3 -w doc\sw2269A-ms98-a-0017.wav
echo:

echo ..\..\bin\oliveanalyze --regions --wav doc\sw2269A-ms98-a-0017.wav --plugin tpd-embed-v1-py3 --domain rus-cts-v1 
call ..\..\bin\oliveanalyze --regions --wav doc\sw2269A-ms98-a-0017.wav --plugin tpd-embed-v1-py3 --domain rus-cts-v1
echo:

echo ..\..\bin\oliveanalyze --wa %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav --pl qbe-tdnn-v7-py3 --do digPtt-v1 --re
call ..\..\bin\oliveanalyze --wa %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav --pl qbe-tdnn-v7-py3 --do digPtt-v1 --re
echo: 

echo ..\..\bin\oliveanalyze  -p qbe-tdnn-v7-py3 -r -d multi-v1 -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
call ..\..\bin\oliveanalyze  -p qbe-tdnn-v7-py3 -r -d multi-v1 -w %TEST_DATA_ROOT%\testSuite\sid\test\JoshuaSarahSpeaking.wav
echo: