@ECHO OFF

SET version="4.3.0"
SET parent_path=%~dp0
cd %parent_path%

SET time_to_sleep=10

echo Starting Core Systems... Service initializations usually need around 20 seconds.

cd ..\serviceregistry\target
START "serviceregistry" /B "cmd /c javaw -jar arrowhead-serviceregistry-%version%.jar > sout_sr.log 2>&1"
echo Service Registry started
timeout /t %time_to_sleep% /nobreak > NUL

cd ..\..\authorization\target
START "" /B "cmd /c javaw -jar arrowhead-authorization-%version%.jar > sout_auth.log 2>&1"
echo Authorization started

cd ..\..\gateway\target
START "" /B "cmd /c javaw -jar arrowhead-gateway-%version%.jar > sout_gateway.log 2>&1"
echo Gateway started

cd ..\..\eventhandler\target
START "" /B "cmd /c javaw -jar arrowhead-eventhandler-%version%.jar > sout_eventhandler.log 2>&1"
echo Event Handler started

cd ..\..\datamanager\target
START "" /B "cmd /c javaw -jar arrowhead-datamanager-%version%.jar > sout_datamanager.log 2>&1"
echo DataManager started

cd ..\..\timemanager\target
START "" /B "cmd /c javaw -jar arrowhead-timemanager-4.1.3.jar > sout_timemanager.log 2>&1"
echo TimeManager started

cd ..\..\gatekeeper\target
START "" /B "cmd /c javaw -jar arrowhead-gatekeeper-%version%.jar > sout_gk.log 2>&1"
echo Gatekeeper started

cd ..\..\orchestrator\target
START "" /B "cmd /c javaw -jar arrowhead-orchestrator-%version%.jar > sout_orch.log 2>&1"
echo Orchestrator started

cd ..\..\choreographer\target
START "" /B "cmd /c javaw -jar arrowhead-choreographer-%version%.jar > sout_choreographer.log 2>&1"
echo Choreographer started

cd ..\..\configuration\target
START "" /B "cmd /c javaw -jar arrowhead-configuration-4.2.0.jar > sout_configuration.log 2>&1"
echo Configuration started

cd ..\..\certificate-authority\target
START "" /B "cmd /c javaw -jar arrowhead-certificate-authority-%version%.jar > sout_ca.log 2>&1"
echo Certificate Authority started

cd %parent_path%

::Kill self
title=arrowheadSecureStarter
FOR /F "tokens=2" %%p in ('"tasklist /v /NH /FI "windowtitle eq arrowheadSecureStarter""') DO taskkill /pid %%p > NUL 2>&1
