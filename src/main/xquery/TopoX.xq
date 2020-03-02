(:~
 :
 : ---------------------------------------
 : TopoX XQuery Function Library Facade
 : ---------------------------------------
 :
 : Copyright (C) 2018 interactive instruments GmbH
 :
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
 :
 : @see     https://jonherrmann.github.io/etf-topox/Developer_documentation.html (will be changed in future releases)
 : @author  Jon Herrmann ( herrmann aT interactive-instruments doT de )
 :
 :)
module namespace topox = 'https://modules.etf-validator.net/topox/1';

import module namespace java = 'java:de.interactive_instruments.etf.bsxm.TopoX';

declare namespace gml='http://www.opengis.net/gml/3.2';
declare namespace ete='http://www.interactive-instruments.de/etf/topology-error/1.0';

declare variable $topox:ERROR_CODES_NON_VISUALIZABLE := (
    'EDGE_NOT_FOUND',
    'INVALID_SURFACE_STRUCTURE',
    'INVALID_ANGLE',
    'EDGE_INVALID',
    'EDGE_MISSING_LEFT',
    'EDGE_MISSING_RIGHT'
);

declare variable $topox:ERROR_CODES := (
    'RING_OVERLAPPING_EDGES',
    'RING_INTERSECTION',
    'HOLE_EMPTY_INTERIOR',
    'FREE_STANDING_SURFACE',
    'FREE_STANDING_SURFACE_DETAILED',
    'POINT_DETACHED',
    'EDGE_POINTS_INVALID',
    'EDGE_INVALID_LEFT',
    'EDGE_INVALID_RIGHT',
    $topox:ERROR_CODES_NON_VISUALIZABLE
);

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
 : Creates a new object for building a topological data structure.
 :
 : Throws BaseXException
 : if the $tempOutputDir directory cannot be used to write files or
 : if the name name already exists.
 :
 : @param  $topologyName name of the topological name
 : @param  $tempOutputDir directory for storing error information
 : @param  $initialEdgeCapacity expected number of edges.
 : This value should be about 1995000 * number of databases (experience value from tests).
 : The number is used to allocate the data structures accordingly and to increase the performance.
 : @return ID of the topology name
 :)
declare function topox:new-topology($topologyName as xs:string, $tempOutputDir as xs:string, $initialEdgeCapacity as xs:integer) as xs:int {
    java:newTopologyBuilder($topologyName, $initialEdgeCapacity, $tempOutputDir)
};

(:~
 : Creates a new object for building a topological data structure.
 :
 : Does not override the error output file and writes all errors to System.out.
 :
 : Note: this method can be used for development purposes and is
 : not intended for production use! It will be removed in later releases.
 :
 : Throws BaseXException
 : if the $tempOutputDir directory cannot be used to write files or
 : if the name name already exists.
 :
 : @param  $topologyName name of the topological name
 : @param  $tempOutputDir directory for storing error information
 : @param  $initialEdgeCapacity expected number of edges.
 : This value should be about 1995000 * number of databases (experience value from tests).
 : The number is used to allocate the data structures accordingly and to increase the performance.
 : @return ID of the topology name
 :)
declare function topox:dev-topology($topologyName as xs:string, $tempOutputDir as xs:string, $initialEdgeCapacity as xs:integer) as xs:int {
    java:devTopologyBuilder($topologyName, $initialEdgeCapacity, $tempOutputDir)
};


(:~
 : Parses GML Surface nodes possessing LineStringSegments and Arcs
 :
 : Errors can be retrieved by calling the topological-errors() function.
 :
 : Throws BaseXException if the $topologyId is unknown
 :
 : @param   $objects that possess gml surfaces with LineStringSegments and Arcs
 : @param   $path not used yet
 : @param   $topologyId ID of the topology
 : @returns dust from CPU fan
 :)
declare function topox:parse-surface($objects as node()*, $path as xs:string, $topologyId as xs:int) as empty-sequence() {
    for $object in $objects
    (: Todo dynamic path :)
    for $geometry in topox:surface-geometries( java:nextFeature($topologyId, $object)/*:position )
    return
        (
        java:nextGeometricObject($topologyId),
        for $segment in $geometry/gml:exterior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
        return
            java:parseSegment($topologyId, $segment, topox:segment-type-to-int($segment/../../local-name())),
        for $interior in $geometry/gml:interior
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
 : Checks the topology for free-standing surfaces.
 :
 : Must be called after the parse-surface() function.
 : Errors of the type FREE_STANDING_SURFACE can be retrieved by
 : calling the topological-errors() function.
 :
 : The error includes only the first found coordinate and the object
 : of the the free-standing surface.
 :
 : Throws BaseXException if the $topologyId is unknown
 :
 : @param   $topologyId ID of the topology to check
 : @returns the number of free-standing surfaces found or 0 if there is only one outer border
 :)
declare function topox:detect-free-standing-surfaces($topologyId as xs:int) as xs:int {
        let $initTime := prof:current-ms()
        let $freeStandingSurfacesCount := java:detectFreeStandingSurfaces($topologyId)
        let $duration := prof:current-ms()-$initTime
        let $logDummy := prof:dump($freeStandingSurfacesCount || " free-standing surfaces detected in " || $duration || "ms")
        return $freeStandingSurfacesCount
};

(:~
 : Checks the topology for free-standing surfaces.
 :
 : Must be called after the parse-surface() function.
 : Errors of the type FREE_STANDING_SURFACE_DETAILED can be retrieved by
 : calling the topological-errors() function.
 :
 : The difference to the detect-free-standing-surfaces() function is
 : that the objects at the outer edges of the free-standing surfaces are output as errors.
 :
 : Throws BaseXException if the $topologyId is unknown
 :
 : @param   $topologyId ID of the topology to check
 : @returns the number of free-standing surfaces found or 0 if there is only one outer border
 :)
declare function topox:detect-free-standing-surfaces-detailed($topologyId as xs:int) as xs:int {
        let $initTime := prof:current-ms()
        let $freeStandingSurfacesCount := java:detectFreeStandingSurfacesWithAllObjects($topologyId)
        let $duration := prof:current-ms()-$initTime
        let $logDummy := prof:dump($freeStandingSurfacesCount || " free-standing surfaces detected in " || $duration || "ms")
        return $freeStandingSurfacesCount
};

(:~
 : Checks the topology for holes.
 :
 : Must be called after the parse-surface() function.
 : Errors can be retrieved by calling the topological-errors() function.
 :
 : Throws BaseXException if the $topologyId is unknown
 :
 : @param   $topologyId ID of the topology to check
 : @returns the number of holes found
 :)
declare function topox:detect-holes($topologyId as xs:int) as xs:int {
    let $initTime := prof:current-ms()
    let $holesCount := java:detectHoles($topologyId)
    let $duration := prof:current-ms()-$initTime
    let $logDummy := prof:dump($holesCount || " holes detected in " || $duration || "ms")
    return $holesCount
};

(:~
 : Creates a new object to validate edges basically the overlapping of boundaries.
 : Only validates correctly if a single boundary object will be parsed.
 :
 : A boundary is exactly on an edge. There must be no overlap with another boundary
 : otherwise an error is reported. In order to lay several boundaries over one edge,
 : several independent boundary checking objects must be created.
 :
 : The edge can be validated against additional criteria, for example to check
 : which objects exist on the left and the right side.
 :
 : Requires an initialized topology object that has already
 : captured topological information with the parse-surface() function.
 : Errors can be retrieved by calling the topological-errors() function.
 :
 : Throws BaseXException if the topologyId is unknown
 :
 : @param  $topologyId ID of the topology
 : @return ID of the boundary check object
 :)
declare function topox:new-validator($topologyId as xs:int) as xs:int {
    java:newEdgeValidator($topologyId)
};

(:~
 : Creates a new object to validate edges. Checks if boundaries overlap with the topology.
 : Also validates correctly if multiple boundary objects will be parsed.
 :
 : A boundary is exactly on an edge. There must be no overlap with another boundary
 : otherwise an error is reported. In order to lay several boundaries over one edge,
 : several independent boundary checking objects must be created.
 :
 : The edge can be validated against additional criteria, for example to check
 : which objects exist on the left and the right side.
 :
 : Requires an initialized topology object that has already
 : captured topological information with the parse-surface() function.
 : Errors can be retrieved by calling the topological-errors() function.
 :
 : Throws BaseXException if the topologyId is unknown
 :
 : @param  $topologyId ID of the topology
 : @return ID of the boundary check object
 :)
declare function topox:new-validator-multiple-boundaries($topologyId as xs:int) as xs:int {
    java:newEdgeValidatorMultipleBoundaries($topologyId)
};

(:~
 : Parses an object and validates.
 :
 : If there is another object with the same boundary, an error is reported.
 :
 : Requires an initialized validator object.
 : Errors can be retrieved by calling the topological-errors() function:
 : - POINT_DETACHED
 : - EDGE_POINTS_INVALID
 :
 : Throws BaseXException if the $boundaryId is unknown
 :
 : @param   $objects that possess gml surfaces with LineStringSegments and Arcs
 : @param   $path not used yet
 : @param   $validatorId ID of the validator
 : @returns nothing
 :)
declare function topox:parse-boundary($objects as node()*, $path as xs:string, $validatorId as xs:int) as empty-sequence() {
    for $object in $objects
    (: Todo dynamic path :)
    for $geometry in java:nextFeature($validatorId, $object)/*:position
    return
    (
        java:nextValidatorObject($validatorId),
        for $geo in topox:curve-geometries($geometry)
        return
            java:parseEdgeToValidate($validatorId, $geo/gml:posList/text())
    )
};

(:~
 : Parses an object and validates the left and the right side with an assertion.
 :
 : If there is another object with the same boundary, an error is reported.
 :
 : Requires an initialized validator object.
 :
 : Errors can be retrieved by calling the topological-errors() function:
 : - POINT_DETACHED
 : - EDGE_POINTS_INVALID
 : - EDGE_INVALID_LEFT
 : - EDGE_INVALID_RIGHT
 : - EDGE_MISSING_LEFT
 : - EDGE_MISSING_RIGHT
 : - EDGE_INVALID
 :
 : The validation functions must accept two xs:long parameters for the
 : right and the left side of the parsed axis. Each parameter represents
 : the geometric object (can be resolved with the topox:geometric-object()
 : function) and the feature (can be resolved with
 : the topox:feature() function). The passed values can be 0!
 :
 : The function must return:
 : - 0 if the validation passes
 : - 1 if the right side violates the assertion, so
 :   EDGE_INVALID_RIGHT and EDGE_MISSING_RIGHT errors will be reported
 : - 2 if the left side violates the assertion, so
 :   EDGE_INVALID_LEFT and EDGE_MISSING_LEFT will be reported
 : - 3 if both side violate the assertion, so
 :   EDGE_INVALID will be reported
 :
 : Throws BaseXException if the $boundaryId is unknown
 :
 : @param   $objects that possess gml surfaces with LineStringSegments and Arcs
 : @param   $path not used yet
 : @param   $validatorId ID of the validator
 : @param   $assertion function to validate the left and the right side
 : @returns nothing
 :)
declare function topox:parse-axis(
    $objects as node()*,
    $path as xs:string,
    $validatorId as xs:int,
    (: right and left :)
    $assertion as function(xs:long, xs:long) as xs:int
) as empty-sequence() {
    for $object in $objects
    (: Todo dynamic path :)
    for $geometry in java:nextFeature($validatorId, $object)/*:position
    return
    (
        java:nextValidatorObject($validatorId),
        for $geo in topox:curve-geometries($geometry)
        return
        (
            java:parseEdgeToValidate($validatorId, $geo/gml:posList/text()),
            java:reportValidationResult(
                $validatorId, $assertion(
                    java:validatorGetEdgeRight($validatorId),
                    java:validatorGetEdgeLeft($validatorId))
            )
        )
    )
};

(:~
 : Returns the document that contains all topological errors found
 :
 : Note: for performance reasons, this a deterministic function. Calling this
 : function after changing the error file might not result in a changed output.
 : Also note that no other function may be called after calling this function.
 :
 : @param  $topologyId ID of the topology
 : @return TopologicalErrors node
 :)
declare function topox:topological-errors-doc($topologyId as xs:int) as node() {
    (: Todo: we will probably have to make some improvements here :)
    doc(java:errorFile($topologyId))/ete:TopologicalErrors[1]
};

(:~
 : Returns all topological error (e) elements from the error document
 :
 : Repeated errors are filtered (they can be retrieved with the topological-errors-doc function).
 :
 : Note: for performance reasons, this a deterministic function. Calling this
 : function after changing the error file might not result in a changed output.
 : Also note that no other function may be called after calling this function.
 :
 : @param  $topologyId ID of the topology
 : @param  $errorCodes errorCodes to filter or ()
 : @return zero or more topological error elements
 :)
declare function topox:topological-errors($topologyId as xs:int, $errorCodes as xs:string*) as element()* {
    topox:topological-errors-doc($topologyId)/e[not(@p) and (topox:check-error-code($errorCodes) or @t = $errorCodes)]
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

(:~
 : Exports features that are part of a topological problem to a GeoJson file
 :
 : Note: until now mainly used for development purposes. Translations are not supported yet.
 :
 : @param  $topologyId ID of the topology
 : @param  $errorCodes errorCodes to filter (optional)
 : @return nothing
 :)
declare function topox:export-erroneous-features-to-geojson($topologyId as xs:int, $geoJsonAttachmentId as xs:string, $errorCodes as xs:string*) as empty-sequence() {
        let $topoErrors := topox:topological-errors($topologyId,  $errorCodes)[not(@t = $topox:ERROR_CODES_NON_VISUALIZABLE)]
        return (
            topox:export-error-points($topologyId, $topoErrors),
            topox:export-features($topologyId, $topoErrors),
            java:attachIssueMap($topologyId, $geoJsonAttachmentId)
        )
};

(:~
 : Exports features that are part of a topological problem to a GeoJson file
 :
 : Note: until now mainly used for development purposes. Translations are not supported yet.
 :
 : @param  $topologyId ID of the topology
 : @return nothing
 :)
declare function topox:export-erroneous-features-to-geojson($topologyId as xs:int, $geoJsonAttachmentId as xs:string) as empty-sequence() {
    topox:export-erroneous-features-to-geojson($topologyId, $geoJsonAttachmentId, ())
};

(:~
 : Returns the TopoX version (SemVer 2 format)
 :
 : @return version and build date as string
 :)
declare function topox:version() as xs:string {
    java:version()
};

(:~
 : Returns the TopoX version including the build version
 :
 : @return version including the build version
 :)
declare function topox:detailed-version() as xs:string {
    java:detailedVersion()
};

(:~
 : Returns information about the topology theme for diagnosis:
 : - current internal object id
 : - number of processed objects
 : - number of stored edges
 : - number of stored coordinates
 : - detected coordinate lookup collisions
 : - detected coordinate lookup errors
 :
 : Note: it is normal that lookup collisions and errors occur
 : in large datasets.
 :
 : @param  $topologyId ID of the topology
 : @return information as string
 :)
declare function topox:diag($topologyId as xs:int) as xs:string {
    java:diag($topologyId)
};

(:~ ---------------------------------------------- PRIVATE FUNCTIONS ---------------------------------------------- :)

(:~
 : Checks if valid error codes are passed
 :
 : Throws an error if a non-empty list of error codes
 : has been passed and an error code is unknown.
 :
 : @param  $errorCodes error code to check
 : @return true if $errorCodes is empty, false otherwise
 :)
declare %private function topox:check-error-code($errorCodes as xs:string*) {
  if (empty($errorCodes)) then
    true()
  else if (not(every $errCode in $errorCodes satisfies $errCode = $topox:ERROR_CODES)) then
    error(xs:QName('ete:INVALID_ERROR_CODE'), 'invalid error codes ' || string-join($errorCodes, ' '))
  else
    false()
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

(:~
 : Export error messages for GeoJson. Only in German, until now...
 :)
declare %private function topox:error-message($error as node()) as xs:string {
    let $is := topox:geometric-object( $error/IS[1]/text() )
    let $isFeature := topox:feature($error/IS[1]/text())
    let $isGmlId := string($is/../../../../@gml:id)
    let $isFeatureId := xs:string($isFeature/@gml:id)

    return if( $error/@t = 'RING_OVERLAPPING_EDGES') then
        let $mesg1 := "\u00DCberlappung im Linienverlauf zu Punkt <br/>" || $error/X || " " || $error/Y ||
            "<br/> am Objekt '" ||
            $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  || "' mit dem "
        let $mesg2 :=
                if ($error/O[1]/text() != 0)
                    then
                        let $overlappingFeature := topox:feature($error/O[1]/text())
                        return
                            let $overlapping := topox:geometric-object( $error/O[1]/text() )
                            let $overlappingGmlId := string($overlapping/../../../../@gml:id)
                            let $overlappingFeatureId := xs:string($overlappingFeature/@gml:id)
                            return " <br/> Objekt '" || $overlappingFeatureId ||
                            "' <br/> mit Geometrie  '" || $overlappingGmlId || "' aufgetreten"
                    else ""
        return $mesg1 || $mesg2
    else if( $error/@t = 'INNER_RING_SELF_INTERSECTION') then
                 let $mesg1 := "Selbstschnitt im Linienverlauf zu Punkt <br/>" || $error/X || " " || $error/Y ||
                     "<br/> am Objekt '" ||
                     $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  || "' mit "
                 let $mesg2 :=
                         if ($error/O[1]/text() != 0)
                             then
                                 let $overlapping := topox:geometric-object( $error/O[1]/text() )
                                 let $overlappingGmlId := string($overlapping/../../../../@gml:id)
                                 return "' <br/> mit Geometrie  '" || $overlappingGmlId || "' aufgetreten"
                             else ""
                 return $mesg1 || $mesg2

    else if( $error/@t = ('FREE_STANDING_SURFACE', 'FREE_STANDING_SURFACE_DETAILED') ) then
                 "Freistehende Fl\u00E4che bei Punkt <br/>" || $error/X || " " || $error/Y ||
                     "<br/> am Objekt '" ||
                     $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  || "' erkannt. "

    else if( $error/@t = 'HOLE_EMPTY_INTERIOR') then
                     "Nicht geschlossene Fl\u00E4che bei Punkt <br/>" || $error/X || " " || $error/Y ||
                         "<br/> am Objekt '" ||
                         $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  || "' erkannt. "

    else if( $error/@t = 'POINT_DETACHED') then
                     "Der Punkt <br/>" || $error/X || " " || $error/Y ||
                         "<br/> am Objekt '" ||
                         $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  ||
                         "' liegt topologisch nicht auf einem anderen Objekt. "

    else if( $error/@t = 'EDGE_POINTS_INVALID') then
                     "Die Punkte <br/>" || $error/X || " " || $error/Y ||
                         "<br/> und " || $error/X2 || " " || $error/Y2 ||
                         "<br/> am Objekt '" ||
                         $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  ||
                         "' sind topologisch nicht direkt verbunden."

    else if( $error/@t = 'EDGE_INVALID_LEFT') then
                     "An den Punkten <br/>" || $error/X || " " || $error/Y ||
                         "<br/> und " || $error/X2 || " " || $error/Y2 ||
                         "<br/> am Objekt '" ||
                         $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  ||
                         "' verletzt das Objekt auf der linken Seite eine Bedingung. "

    else if( $error/@t = 'EDGE_INVALID_RIGHT') then
                     "An den Punkten <br/>" || $error/X || " " || $error/Y ||
                         "<br/> und " || $error/X2 || " " || $error/Y2 ||
                         "<br/> am Objekt '" ||
                         $isFeatureId || "' <br/> bei Geometrie <br/> '" || $isGmlId  ||
                         "' verletzt das Objekt auf der rechten Seite eine Bedingung. "

    else
        let $mesg1 := "\u00DCberlappung oder \u00DCberschneidung im Linienverlauf zu Punkt <br/>" || $error/X || " " || $error/Y ||
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
 : Export error points for GeoJson
 :)
declare %private function topox:export-error-points($topologyId as xs:int, $topoErrors as item()*) as empty-sequence() {
    for $e in $topoErrors
    return
        java:writeGeoJsonPOI( $topologyId, string($e/@i), topox:error-message($e), string($e/X), string($e/Y) )
};

(:~
 : Export features with errros as GeoJson
 :)
declare %private function topox:export-features($topologyId as xs:int, $topoErrors as item()*) as empty-sequence() {
    let $objectMap := map:merge(
            for $errObj in $topoErrors/*[local-name() = ('IS', 'CW', 'CCW', 'O')]/text()
            where $errObj != 0
        return map:entry(java:objPreAsGeoPre(xs:long($errObj)),()))
    let $objects := map:for-each($objectMap, function($pre, $o) { topox:geometric-object($pre) } )
    for $obj in $objects
    return (
        java:startGeoJsonFeature($topologyId, xs:string($obj/@gml:id)),
        for $surfaceGemoetry in topox:surface-geometries( $obj/*:position )
        return (
            for $segment in $surfaceGemoetry/gml:exterior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
                return
            java:addGeoJsonPolygonCoordinates($topologyId, $segment),
            for $interior in $surfaceGemoetry/gml:interior
            return
            (
                java:nextGeoJsonPolygonInterior($topologyId),
                for $segment in $interior/gml:Ring/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')]/gml:posList/text()
                return
                java:addGeoJsonPolygonCoordinates($topologyId, $segment)
            )
        ),(
            for $curveGeometry in topox:curve-geometries( $obj/*:position )
            return (
                for $curve in $curveGeometry/gml:posList/text()
                    return
                java:addGeoJsonCurveCoordinates($topologyId, $curve)
            )
        )
    )
};

(:~
 : Returns all supported surface geometries of an object:
 : - Surface
 : - MultiSurface
 : - PolyhedralSurface
 : - CompositeSurface
 :)
declare %private function topox:surface-geometries($obj as node()*) as node()* {
    $obj/gml:*[local-name() = ('Surface', 'PolyhedralSurface')]/gml:patches/gml:PolygonPatch,
    $obj/gml:MultiSurface/gml:*[local-name() = ('surfaceMember', 'surfaceMembers')]/gml:Polygon,
    $obj/gml:MultiSurface/gml:*[local-name() = ('surfaceMember', 'surfaceMembers')]/gml:CompositeSurface/gml:surfaceMember//gml:*[local-name() = ('PolygonPatch', 'Polygon')],
    $obj/gml:MultiSurface/gml:*[local-name() = ('surfaceMember', 'surfaceMembers')]/gml:Surface/gml:patches/gml:PolygonPatch,
    $obj/gml:MultiSurface/gml:*[local-name() = ('surfaceMember', 'surfaceMembers')]/gml:PolyhedralSurface/gml:patches/gml:PolygonPatch,
    $obj/gml:CompositeSurface/gml:surfaceMember/gml:Polygon,
    $obj/gml:CompositeSurface/gml:surfaceMember/gml:CompositeSurface/gml:surfaceMember//gml:*[local-name() = ('PolygonPatch', 'Polygon')],
    $obj/gml:CompositeSurface/gml:surfaceMember/gml:Surface/gml:patches/gml:PolygonPatch,
    $obj/gml:CompositeSurface/gml:surfaceMember/gml:PolyhedralSurface/gml:patches/gml:PolygonPatch
};

(:~
 : Returns all supported curve geometries of an object:
 : - Curve
 : - CompositeCurve
 : - OrientableCurve
 : - LineString
 :
 : The geometries must have 3 coordinates
 :)
declare %private function topox:curve-geometries($obj as node()*) as node()* {
    $obj/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')],
    $obj/gml:CompositeCurve/gml:curveMember/gml:Curve/gml:segments/gml:*[local-name() = ('LineStringSegment', 'Arc')],
    $obj/gml:OrientableCurve/gml:baseCurve/gml:LineString,
    $obj/gml:LineString
};
