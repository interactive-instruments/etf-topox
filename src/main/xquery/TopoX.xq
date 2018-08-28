(:~

 : ---------------------------------------
 : TopoX XQuery Function Library Wrapper
 : ---------------------------------------

 : Copyright (C) 2018 interactive instruments GmbH

 : Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 : the European Commission - subsequent versions of the EUPL (the "Licence");
 : You may not use this work except in compliance with the Licence.
 : You may obtain a copy of the Licence at:
 :
 : https://joinup.ec.europa.eu/software/page/eupl
 :
 : Unless required by applicable law or agreed to in writing, software
 : distributed under the Licence is distributed on an "AS IS" basis,
 : WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 : See the Licence for the specific language governing permissions and
 : limitations under the Licence.

 : @see     https://docs.etf-validator.net/Developer_manuals/Topox.html
 : @author  Jon Herrmann ( herrmann aT interactive-instruments doT de )
 
 :)
module namespace topox = 'https://modules.etf-validator.net/topox';

import module namespace java = 'java:de.interactive_instruments.etf.bsxm.TopoX';

declare namespace gml='http://www.opengis.net/gml/3.2';
declare namespace ete='http://www.interactive-instruments.de/etf/topology-error/1.0';

(:~
 : Initialize the TopoX module with the database name
 :
 : Must be run before using other topox functions.
 : Database names must be suffixed with a three digits index, i.e. DB-000.
 :
 : Throws BaseXException if database name is not suffixed with a three digits index.
 :
 : @param  $dbName full database name ( i.e. DB-000 )
 : @param  $dbCount number of databases
 : @return full database name $dbName
 :)
declare function topox:init-db($dbName as xs:string, $dbCount as xs:short) as xs:string {
    java:initDb($dbName, $dbCount)
};

(:~
 : Creates a new object for building a topological data structure
 :
 : Throws BaseXException if the $tempOutputDir directory cannot be used to write files.
 :
 : @param  $topologyName Name of the topology
 : @param  $tempOutputDir directory for storing error information
 : @param  $initialEdgeCapacity expected number of edges, should be 1995000 * number of databases
 : @return ID of the topology
 :)
declare function topox:new-topology($topologyName as xs:string, $tempOutputDir as xs:string, $initialEdgeCapacity as xs:integer) as xs:int {
    java:newTopologyBuilder($topologyName, 'path', '@gml:id', 'adv:position/gml:Surface', $initialEdgeCapacity, $tempOutputDir)
};

declare function topox:dev-topology($topologyName as xs:string, $tempOutputDir as xs:string, $initialEdgeCapacity as xs:integer) as xs:int {
    java:devTopologyBuilder($topologyName, 'path', '@gml:id', 'adv:position/gml:Surface', $initialEdgeCapacity, $tempOutputDir)
};

(:~
 : Parses GML Surface nodes possessing LineStringSegments and Arcs
 :
 : @param   $objects that possess gml surfaces with LineStringSegments and Arcs
 : @param   $path not used yet
 : @param   $topologyId ID of the topology
 : @returns dust from CPU fan
 :)
declare function topox:parse-surface($objects as node()*, $path as xs:string, $topologyId as xs:int) as empty-sequence() {
    for $object in $objects
    (: Todo dynamic path :)
    for $geometry in java:nextFeature($topologyId, $object)/*:position/gml:Surface
    return
        (
        java:nextGeometricObject($topologyId),
        for $segment in $geometry/gml:patches/gml:PolygonPatch/gml:exterior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
        return
            java:parseSegment($topologyId, $segment, topox:segment-type-to-int($segment/../../local-name())),
        for $interior in $geometry/gml:patches/gml:PolygonPatch/gml:interior
        return
            (
            java:nextInterior($topologyId),
            for $segment in $interior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
            return
                java:parseSegment($topologyId, $segment, topox:segment-type-to-int($segment/../../local-name()))
            )
        )
};

(:~
 : Returns the document that contains all topological errors found
 :
 : @param  $topologyId ID of the topology
 : @return TopologicalErrors node
 :)
declare function topox:topological-errors($topologyId as xs:int) as node() {
    doc(java:errorFile($topologyId))/ete:TopologicalErrors
};

(:~
 : Returns the corresponding segment reported in an topological error
 :
 : @param  $compressedValue compressed value to decode
 : @return erroneous segment
 :)
declare function topox:geometric-object($compressedValue as xs:long) as node() {
    db:open-pre(java:dbname($compressedValue), java:pre($compressedValue))
};

(:~
 : Returns the feature reported in an topological error
 :
 : @param  $compressedValue compressed value to decode
 : @return feature
 :)
declare function topox:feature($compressedValue as xs:long) as node() {
    db:open-pre(java:dbname($compressedValue), java:preObject($compressedValue))
};


declare %private function topox:error-message($error as node()) as xs:string {
    let $is := topox:geometric-object( $error/IS[1]/text() )
    let $isFeature := topox:feature($error/IS[1]/text())
    let $isGmlId := string($is/../../../../@gml:id)
    let $isFeatureId := xs:string($isFeature/@gml:id)
    let $mesg1 := "\u00DCberlappung oder \u00DCberschneidung bei Punkt <br/>" || $error/X || " " || $error/Y ||
        "<br/> am Objekt '" || 
        $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  || "' aufgetreten : "

    let $mesg2 :=
    if ($error/CW[1]/text() != 0)
        then
            let $cwFeature := topox:feature($error/CW[1]/text())
            return
                let $cw := topox:geometric-object( $error/CW[1]/text() )
                let $cwGmlId := string($cw/../../../../@gml:id)
                let $cwFeatureId := xs:string($cwFeature/@gml:id)
                return " <br/> angrenzendes Objekt '" || $cwFeatureId ||
                "' <br/> mit Geometrie  '" || $cwGmlId || "'"
        else ""

    let $mesg3 :=
    if ($error/CCW[1]/text() != 0)
        then
            let $ccwFeature := topox:feature($error/CCW[1]/text())
            return
                let $ccw := topox:geometric-object($error/CCW[1]/text())
                let $ccwGmlId := string($ccw/../../../../@gml:id)
                let $ccwFeatureId := xs:string($ccwFeature/@gml:id)
                return " <br/> angrenzendes Objekt '" || $ccwFeatureId ||
                "' <br/> mit Geometrie  '" || $ccwGmlId || "'"
        else ""
    return $mesg1 || $mesg2 || $mesg3
};

(:~
 : Exports features that are part of a topological problem to a GeoJson file
 :
 : @param  $topologyId ID of the topology
 : @return nothing
 :)
declare function topox:export-erroneous-features-to-geojson($topologyId as xs:int, $attachmentId as xs:string) as empty-sequence() {
        let $topoErrors := topox:topological-errors($topologyId)/e[@t = "RING_INTERSECTION"]
        return (
            topox:export-error-points($topologyId, $topoErrors),
            topox:export-features($topologyId, $topoErrors),
            java:attachIssueMap($topologyId, $attachmentId)
        )
};

declare %private function topox:export-error-points($topologyId as xs:int, $topoErrors as item()*) as empty-sequence() {
    for $e in $topoErrors
    return
        java:writeGeoJsonPointFeature( $topologyId, string($e/@i), topox:error-message($e), string($e/X), string($e/Y) )
};

declare %private function topox:export-features($topologyId as xs:int, $topoErrors as item()*) as empty-sequence() {
    
    let $objectMap := map:merge(
            for $errObj in $topoErrors/*[local-name() = ('IS', 'CW', 'CCW')]/text()
            where $errObj != 0
        return map:entry(java:objPreAsGeoPre(xs:long($errObj)),()))
    let $objects := map:for-each($objectMap, function($pre, $o) { topox:geometric-object($pre) } )
    for $obj in $objects
    return (
        java:startGeoJsonFeature($topologyId, xs:string($obj/@gml:id)),
        for $geometry in $obj/*:position/gml:Surface
        return (
            for $segment in $geometry/gml:patches/gml:PolygonPatch/gml:exterior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
                return
            java:addGeoJsonCoordinates($topologyId, $segment),
            for $interior in $geometry/gml:patches/gml:PolygonPatch/gml:interior
            return
            (
                java:nextGeoJsonInterior($topologyId),
                for $segment in $interior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
                return
                java:addGeoJsonInteriorCoordinates($topologyId, $segment)
            )
        )
    )
};

(:~
 : Maps an Arc to integer 1 and other types to 2
 :
 : @param  $segmentType gml local segment name
 : @return segment type as int
 :)
declare %private function topox:segment-type-to-int($segmentType as xs:string) as xs:int {
    if ($segmentType eq 'Arc') then
        xs:int(1)
    else
        xs:int(2)
};

