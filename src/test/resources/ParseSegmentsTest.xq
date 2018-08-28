declare namespace adv='http://www.adv-online.de/namespaces/adv/gid/6.0';
declare namespace wfsAdv='http://www.adv-online.de/namespaces/adv/gid/wfs';
declare namespace gml='http://www.opengis.net/gml/3.2';

import module namespace topox = 'https://modules.etf-validator.net/topox';


declare function local:log($text as xs:string) as empty-sequence()
{
    prof:dump($text)
};

declare variable $modellart external := "DLKM";
declare variable $dbname := 'etf-topox-006';

let $db := db:open(topox:init-db($dbname, xs:short(1)))
let $dbCount := 3
(: Calculated factor. Must be verified in tests :)
let $initialEdgeCapacity := xs:int($dbCount * 1995000)


let $surfaces := $db/adv:AX_Bestandsdatenauszug/adv:enthaelt/wfsAdv:FeatureCollection/
        gml:featureMember/*[adv:modellart/adv:AA_Modellart[adv:advStandardModell = $modellart] and
                local-name() = (
                    'AX_Fliessgewaesser', 'AX_Hafenbecken', 'AX_Meer', 'AX_StehendesGewaesser',
                    'AX_Bergbaubetrieb', 'AX_FlaecheBesondererFunktionalerPraegung', 'AX_FlaecheGemischterNutzung',
                    'AX_Friedhof', 'AX_Halde', 'AX_IndustrieUndGewerbeflaeche', 'AX_Siedlungsflaeche',
                    'AX_SportFreizeitUndErholungsflaeche', 'AX_TagebauGrubeSteinbruch', 'AX_Wohnbauflaeche',
                    'AX_FlaecheZurZeitUnbestimmbar', 'AX_Gehoelz', 'AX_Heide', 'AX_Landwirtschaft', 'AX_Moor',
                    'AX_Sumpf', 'AX_UnlandVegetationsloseFlaeche', 'AX_Wald', 'AX_Bahnverkehr', 'AX_Flugverkehr',
                    'AX_Platz', 'AX_Schiffsverkehr', 'AX_Strassenverkehr', 'AX_Weg'
                ) and
                not(adv:hatDirektUnten) and adv:position/gml:Surface]

let $logMessage1 := local:log("Initializsing TopoX. This may take a while...")
let $initTime := prof:current-ms()

let $topoId := topox:new-topology(
        'TatsaechlicheNutzung',
        "/tmp/",
        $initialEdgeCapacity
)


let $duration := prof:current-ms()-$initTime
let $logMessage2 := local:log(" TopoX initialized in " || $duration || " ms" )
let $dummy := topox:parse-surface($surfaces, 'adv:position/gml:Surface', $topoId)

(:
  To view file on local machine, temporary toggle
  security.fileuri.strict_origin_policy to false in Firefox
:)
return topox:export-erroneous-features-to-geojson($topoId, "Map")
