declare namespace adv='http://www.adv-online.de/namespaces/adv/gid/6.0';
declare namespace wfsAdv='http://www.adv-online.de/namespaces/adv/gid/wfs';
declare namespace wfsadv='http://www.adv-online.de/namespaces/adv/gid/wfs';
declare namespace gml='http://www.opengis.net/gml/3.2';
declare namespace ete='http://www.interactive-instruments.de/etf/topology-error/1.0';
declare namespace uuid='java.util.UUID';

import module namespace topox = 'https://modules.etf-validator.net/topox/1';


declare function local:log($text as xs:string) as empty-sequence()
{
  prof:dump($text)
};

declare variable $dbname external := 'TOPOX-JUNIT-TEST-DB-000';
declare variable $tmpOutputDirectory external := 'tmp/bsx';

declare variable $modellart external := "DLKM";

let $db := db:open(topox:init-db($dbname, xs:short(1)))
let $dbCount := 3
(: Calculated factor. Must be verified in tests :)
let $initialEdgeCapacity := xs:int($dbCount * 1995000)


let $features := prof:time( ($db/*:AX_Bestandsdatenauszug/*:enthaelt/*:FeatureCollection/*:featureMember/* | $db/*:AX_Einrichtungsauftrag/*:neueObjekte/*:Transaction/*:Insert/* | $db/*:AX_Fortfuehrungsauftrag/*:geaenderteObjekte/*:Transaction/*:Insert/* | $db/*:AX_Fortfuehrungsauftrag/*:geaenderteObjekte/*:Transaction/*:Replace/* | $db/*:AX_NutzerbezogeneBestandsdatenaktualisierung_NBA/*:geaenderteObjekte/wfsadv:Transaction/wfsadv:Insert/* | $db/*:AX_NutzerbezogeneBestandsdatenaktualisierung_NBA/*:geaenderteObjekte/*:Transaction/*:Replace/*), 'Objekte: ')


let $logMessage1 := local:log("Initializsing TopoX " || topox:detailed-version() || ". This may take a while...")
let $initTime := prof:current-ms()

let $topoId := topox:new-topology(
  'Tatsaechliche_Nutzung',
  $tmpOutputDirectory,
  $initialEdgeCapacity
)

let $duration := prof:current-ms()-$initTime
let $dummy := local:log("TopoX initialized in " || $duration || " ms" )

let $initTime := prof:current-ms()
let $dummy := topox:parse-surface($features, 'adv:position/gml:Surface', $topoId)
let $duration := prof:current-ms()-$initTime
let $dummy := local:log("Topology built in " || $duration || " ms" )
let $dummy := local:log("Feature count " || count($features) )

let $dummy := topox:detect-holes($topoId)

let $dummy := topox:detect-free-standing-surfaces($topoId)

(:
  To view file on local machine, temporary toggle
  security.fileuri.strict_origin_policy to false in Firefox
:)
let $initTime := prof:current-ms()
let $dummy := topox:export-erroneous-features-to-geojson($topoId, "Map_" || uuid:randomUUID() || ".js")
let $duration := prof:current-ms()-$initTime
let $dummy := local:log(" Results exported in " || $duration || " ms" )

return topox:topological-errors-doc($topoId)

