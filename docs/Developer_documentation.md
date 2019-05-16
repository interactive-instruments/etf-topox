Developing tests with TopoX
============================

This manual requires a working installation of BaseX with the TopoX plugin installed.

Before TopoX can be used in a test, the library **must be initialized** with the
name of the database and the number of databases that will be used:

```XQuery

topox:init-db("MY-DB-000", xs:short(1))
```
The first parameter (`"MY-DB-000"`) is the database name.

Database names must be suffixed with a three digits index starting with `000`. This is necessary because internally the suffix is used to encode references (see architecture documentation for details).

As second parameter the number of databases is passed.

The Features of interest are selected and projected. For example with this Query:

```XQuery

let $surfaces := $db/adv:AX_Bestandsdatenauszug/adv:enthaelt/wfsAdv:FeatureCollection/gml:featureMember/*[adv:modellart/adv:AA_Modellart[adv:advStandardModell = $modellart] and local-name() = ('AX_Fliessgewaesser', 'AX_Schiffsverkehr', 'AX_Strassenverkehr') and not(adv:hatDirektUnten) and adv:position/gml:Surface]

```

Multiple topologies can be set up for a set of Features and checked. The initialization of a new topology object -which represents a **topological theme**- is done with:

```XQuery

let $topoId := topox:new-topology(
'Tatsaechliche_Nutzung',
'/tmp',
$initialEdgeCapacity
)

```

where the first parameter is the name of the topology theme, the second one a path to a temporary output directory as string and the third one the expected number of edges that should exist in the data. The third parameter is used to initialize the data structure in an appropriate size and thus to increase the performance. It can be specified smaller than the actual number, but then internal reallocations and performance losses can be the result. In tests good results were achieved with a value of `1995000 * number of databases`.

The call returns an integer as identifier for the topological theme that
must be used in all further function calls.

The parsing and validation is started with:

```XQuery

let $dummy := topox:parse-surface($surfaces, 'adv:position/gml:Surface', $topoId)

```

The Features are passed in the first parameter. The second parameter is the path to the geometry properties starting from the passed Features. At present, GML LineStringSegments and Arcs are processed.

This function call will take some time depending on the amount of data.

During the parsing, errors are written to a temporary file. The detected errors include intersections and overlapping edges (see RING\_OVERLAPPING\_EDGES and RING\_INTERSECTION error codes). The errors can be retrieved by using the topological-errors() function:

```XQuery

let $errors := topox:topological-errors($topoId, ('RING_INTERSECTION'))

for $error in $errors
	let $isFeature := topox:feature($error/IS[1]/text())
	let $isFeatureId := xs:string($isFeature/@gml:id)
order by $isFeatureId
	return $isFeatureId || " : intersection at point " || $error/X/text() || " " $error/Y/text()

```

The structure that the topological-errors() function returns is described in the **Topological errors types** section.

A complete example can be found [here](../src/test/resources/ddt/queries/default.xq).


Advanced features
==================

The following post processing features must be called **after** the topological data structure has been built ( parse-surface() has been called ).

Detection of holes
------------------

Holes -the surface of a Feature with an inner boundary that is not filled by another surface- can be detected by using the detect-holes() function:

```
let $holeCount := topox:detect-holes($topoId)
```

The function returns the number of found holes. All detected issues are saved with the HOLE\_EMPTY\_INTERIOR error code and can be retrieved with the topological-errors() function.


Detection of free-standing surfaces
-----------------------------------

Free-standing surfaces can be detected by using the detect-free-standing-surfaces() function:

```
let $freeStandingSurfaceCount := topox:detect-free-standing-surfaces($topoId)
```

The function returns the number of found free-standing surfaces.

All detected issues are saved with the FREE\_STANDING\_SURFACE error code and can be retrieved with the topological-errors() function.

Check boundaries
----------------

Some objects can describe certain boundaries, like units of administration dividing
areas. With TopoX it can be tested whether the boundaries of the administrative units are exactly on the boundary points and edges of other features.

Boundaries can be condensed like topological themes and must be created with:

```
(: Create the validator object :)
let $validatorId := topox:new-validator($topoId)
```

where the parameter references a topological theme as basis for checks.

The second call to start the parsing of boundaries, is very similar to the parse-surface() function.

```
(: Start border validation :)
let $borderParsingDummy := topox:parse-boundary($borders, 'adv:position/gml:*', $validatorId)
```

If the points or edges of the boundaries do not overlap exactly with the basis
data, POINT\_DETACHED and EDGE\_POINTS\_INVALID errors are reported in the error file.


Validate objects along an axis on the left and right side.
----------------

With TopoX you can walk along a topological axis -e.g. a road axis or boundaries-
and check whether the points are defined and whether the objects on the left
and/or right side meet certain requirements.

```

(:
: Define a higher-order function to check the objects on the right and left side
:
: 0 no error
: @returns 0 no error, 1 error on the right, 2 error on the left, 3 error on both sides
:)
let $assertion := function($right as xs:long, $left as xs:long) {
	if(not($right eq 0)) then
		let $rightFeature := topox:feature($right)
		(: right feature is set, no error :)
		return xs:int(0)
	(: report an error on the right :)
	else xs:int(1)
}

(: Create the validator object :)
let $validatorId := topox:new-validator($topoId)

(: Start axis parsing :)
let $axisParsingDummy := topox:parse-axis($borders, 'adv:position/gml:*', $validatorId, $assertion)
```

If the points or edges of the axis do not overlap exactly with the basis
data, POINT\_DETACHED and EDGE\_POINTS\_INVALID errors are reported in the
error file.

The assertion function must return one of four integer codes:

- 0 if the assertion passes
- 1 if the right side violates the assertion. This reports either an
EDGE\_INVALID\_RIGHT error or an EDGE\_MISSING\_RIGHT depending on whether
the object exists.
- 2 if the left side violates the assertion. This reports either an
EDGE\_INVALID\_LEFT error or an EDGE\_MISSING\_LEFT depending on whether
the object exists.
- 3 if both sides violate the assertion. EDGE\_INVALID will be reported.



Create issue map (experimental)
----------------

This function will export a map with the topological errors as HTML file to the temp folder. This feature is still very experimental and intended for developers until now.

```
let $dummyMap := topox:export-erroneous-features-to-geojson($topoId, "Map")
```


Topological errors types
========================

Structure
---------

TopoX exposes the errors found via two functions: topological-errors-doc() and topological-errors(). The first function can be used to retrieve all errors as one node, the second one accepts a parameter to filter the errors for specific error types:

```XQuery

(: Get all erros :)
let $allErrors := topological-errors-doc($topoId)

(: Get only errors of the type RING\_INTERSECTION and the FREE\_STANDING\_SURFACE :)
let $ringAndFreeStandingSurfaceErrors := topological-errors($topoId,
		('RING_INTERSECTION', 'FREE_STANDING_SURFACE'))
```

The internal error structure, completly returned by the topological-errors-doc() function, looks like this:

```xml
<ete:TopologicalErrors xmlns:ete="http://www.interactive-instruments.de/etf/topology-error/1.0"
	name="Tatsaechliche_Nutzung">
	<e i="1" t="RING_INTERSECTION">
		<IS>519785877976</IS>
		<X>367300.055</X>
		<Y>5614385.776</Y>
		<CW>579820659004</CW>
		<CCW>609885430083</CCW>
	</e>
	<e i="2" t="FREE_STANDING_SURFACE">
		<X>364965.726</X>
		<Y>5620249.802</Y>
		<IS>365113240849</IS>
	</e>
</ete:TopologicalErrors>
```

The structure's root is the _TopologicalErrors_ element and _name_ attribute represents the topological theme. Below the root element, the errors can be found ( _e_ elements). topological-errors() will only return these (filtered) error *e* elements.

Each error possesses an simple integer ID ( _i_ attribute ) and the error type ( _t_ attribute). The names and attributes are abbreviated to support faster parsing of the error file. An error code can possess various properties which will be described below.

The X and Y properties always stand for the X and Y coordinates. All other properties (IS, CW, CCW) reference a feature and its geometry in the database. If an error is returned more than one time for a point, a _p_ attribute (for previous) is added to the next error, which may indicate fault masking. The value of _p_ referes to the first occurance of the error (_i_ value). topological-errors() does not return these repeated errors.

The feature can be queried with the `feature()`, the geometry with `geometric-object()` function:

```XQuery

let $error := topox:topological-errors($topoId, ('RING_INTERSECTION'))[1]

(: Feature gml:id :)
let $feature := topox:feature($error/CW[1]/text())
let $featureId := xs:string($feature/@gml:id)

(: Geometry gml:id :)
let $geometry := topox:geometric-object($error/CW[1]/text())
let $geometryId := topox:geometric-object($feature/../../../../@gml:id)

```

Even if multiple databases are used, the objects are automatically fetched from the corresponding database.


Error Codes
-----

### RING\_OVERLAPPING\_EDGES

There are two Features that define an edge at two points. The features lie on the same side and therefore overlap. The *IS* property references the first object that is on the edge, the *O* refernces the object that collides with the existing one. The *X, Y* properties may reference either the start or the end point of the edge.

### RING\_INTERSECTION

There are at least three edges that are connected at one point (*X, Y* properties). Due to their angles, two edges intersect in the course of the lines. The *IS* property references the object where the error has been detected. *CW* is the object that is connected counter-clockwise to the edge, *CWW* is the object that is connected counter-clockwise to the edge.

### HOLE\_EMPTY\_INTERIOR

The surface of a Feature with an inner boundary is not filled by the surface of another Feature. The *IS* property (and *X, Y* properties) references the object where the error has been detected.

### FREE\_STANDING\_SURFACE

There are at least two free-standing surfaces. All surfaces except the largest, with the most edges, are reported. The *IS* property (and *X, Y* properties) references the object where the error has been detected.

### POINT\_DETACHED

A point (*X, Y* properties) was defined that could not be found in the topological basis data. The *IS* property references the object where the error has been detected.

### EDGE\_POINTS\_INVALID

An edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) was defined that could not be found in the topological basis data. The *IS* property references the object where the error has been detected.


### EDGE\_MISSING\_LEFT

The validation for the left side of an edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) failed due to a missing object.

### EDGE\_MISSING\_RIGHT

The validation for the right side of an edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) failed due to a missing object.

### EDGE\_INVALID\_LEFT

The left side of an edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) violates an assertion. The *IS* property references the object
where the error has been detected.

### EDGE\_INVALID\_RIGHT

The right side of an edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) violates an assertion. The *IS* property references the object
where the error has been detected.

### EDGE\_INVALID

The right and left side of an edge (*X, Y* reference start point of the edge, *X2, Y2* the end point) violate an assertion. The *R* property references the object on the right
side, the *L* property references the object on the left side where the errors
have been detected.

### EDGE\_NOT\_FOUND
An edge could not be found. This is most likely a consequential error if others occurred and for instance the connection between two points has been invalidated in the data structure. If this is the only type of error that occurred, then this may indicate a bug in the software.

### INVALID\_ANGLE
An error occurred while connecting an additional edge to a point. Attempting to traverse the angles of all connected edges has exceeded the maximum number of possible steps. If this is the only type of error that occurred, then this may indicate a bug in the software.

**Note:** In general the EDGE\_NOT\_FOUND and the INVALID\_ANGLE errors can be ignored if errors have previously been reported with other error codes.
