# etf-topox

## Building the library
Run the 'gradlew xar' command to build an EXPath package for BaseX.

## Installing in BaseX
Download the file from [here](https://github.com/jonherrmann/etf-topox/releases/download/0.9.0/TopoX-0.9.0.xar).
In the BaseX GUI, select _Options_ -> _Packages_ and select the file with _Install..._

Or create an update XQuery script with

```XQuery


import module namespace repo = 'http://basex.org/modules/repo';

let $d :=
try { repo:delete('ETF TopoX') ||  prof:dump('Uninstalled') }catch * { () }
return repo:install('https://github.com/jonherrmann/etf-topox/releases/download/0.9.0/TopoX-0.9.0.xar?access_token=GITHUB_TOKEN'),
repo:list()

```

where GITHUB_TOKEN is your private GITHUB access token.

# Develop

Download [this](https://github.com/jonherrmann/etf-topox/blob/master/src/test/resources/ParseSegmentsTest.xq) XQuery file
as starting point.

- Create a Database with the test files. Note: the Database names must be suffixed with a three digits index, i.e. 'etf-topox-000' .

- the output will be generated to the _tmp_ directory, which can be changed in the topox:new-topology() call.

- The interface is described [here](https://github.com/jonherrmann/etf-topox/blob/master/src/main/xquery/TopoX.xq)
