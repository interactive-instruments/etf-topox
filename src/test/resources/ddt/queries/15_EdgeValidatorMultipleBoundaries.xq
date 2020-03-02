declare namespace adv='http://www.adv-online.de/namespaces/adv/gid/6.0';
declare namespace wfsAdv='http://www.adv-online.de/namespaces/adv/gid/wfs';
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


let $surfaceLocalNames := ('AX_Fliessgewaesser', 'AX_Hafenbecken', 'AX_Meer', 'AX_StehendesGewaesser', 'AX_Bergbaubetrieb', 'AX_FlaecheBesondererFunktionalerPraegung', 'AX_FlaecheGemischterNutzung', 'AX_Friedhof', 'AX_Halde', 'AX_IndustrieUndGewerbeflaeche', 'AX_Siedlungsflaeche', 'AX_SportFreizeitUndErholungsflaeche', 'AX_TagebauGrubeSteinbruch', 'AX_Wohnbauflaeche', 'AX_FlaecheZurZeitUnbestimmbar', 'AX_Gehoelz', 'AX_Heide', 'AX_Landwirtschaft', 'AX_Moor', 'AX_Sumpf', 'AX_UnlandVegetationsloseFlaeche', 'AX_Wald', 'AX_Bahnverkehr', 'AX_Flugverkehr', 'AX_Platz', 'AX_Schiffsverkehr', 'AX_Strassenverkehr', 'AX_Weg')
let $surfaces := $db/*:AX_Bestandsdatenauszug/*:enthaelt/wfsAdv:FeatureCollection/gml:featureMember/*[local-name() = $surfaceLocalNames]

let $curves := $db/*:AX_Bestandsdatenauszug/*:enthaelt/wfsAdv:FeatureCollection/gml:featureMember/*[local-name() = "AX_Gebietsgrenze"][*:artDerGebietsgrenze = ('7101','7102')]

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
let $dummy := topox:parse-surface($surfaces, 'adv:position/gml:Surface', $topoId)
let $duration := prof:current-ms()-$initTime
let $dummy := local:log("Topology built in " || $duration || " ms" )

let $boundaryId := topox:new-validator-multiple-boundaries($topoId)
let $borderParsingTime := prof:current-ms()
(: Der zweite Parameter wird derzeit ignoriert, ggfs. in Zukunft Funktion nutzen. :)
let $borderParsingDummy := topox:parse-boundary($curves, '*:position/gml:*', $boundaryId)
let $borderParsingDuration := prof:current-ms()-$borderParsingTime
let $borderParsingLog := local:log("Borders built in " || $borderParsingDuration || " ms" )

let $errorCodes := ('POINT_DETACHED', 'EDGE_POINTS_INVALID')
let $errors := topox:topological-errors($topoId, $errorCodes)


(:
  To view file on local machine, temporary toggle
  security.fileuri.strict_origin_policy to false in Firefox
:)
let $initTime := prof:current-ms()
let $dummy := topox:export-erroneous-features-to-geojson($topoId, "Map_" || uuid:randomUUID() || ".js")
let $duration := prof:current-ms()-$initTime
let $dummy := local:log(" Results exported in " || $duration || " ms" )

return topox:topological-errors-doc($topoId)

