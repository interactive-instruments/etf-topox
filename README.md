# etf-topox

## Building the library
Run the 'gradlew xar' command to build an EXPath package for BaseX.

## Installing in BaseX
Download the file from [here](https://github.com/jonherrmann/etf-topox/releases/download/0.9.3/TopoX-0.9.3.xar).
In the BaseX GUI, select _Options_ -> _Packages_ and select the file with _Install..._

Or create an update XQuery script with

```XQuery


import module namespace repo = 'http://basex.org/modules/repo';

let $d :=
try { repo:delete('ETF TopoX') ||  prof:dump('Uninstalled') }catch * { () }
return repo:install('https://github.com/jonherrmann/etf-topox/releases/download/0.9.3/TopoX-0.9.3.xar'),
repo:list()

```

## Developer quick start guide

Download [this](src/test/resources/ddt/queries/default.xq) XQuery file
as starting point.

- Create a Database with the test files. Note: the Database names must be suffixed with a three digits index, edgeIndex.e. 'etf-topox-000' .

- the output will be generated to the _tmp_ directory, which can be changed in the topox:new-topology() call.

## Developer documentation

The developer documentation can be found [here](docs/Developer_documentation.md)

## Architecture documentation

The arc42 based architecture documentation can be found [here](docs/Architecture_documentation.md)

## Todos:

- Refactoring (ports, class visibility)
- Test data
