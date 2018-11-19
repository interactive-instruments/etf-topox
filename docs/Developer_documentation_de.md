Entwicklung von Tests mit TopoX
============================

Die folgende exemplarische Vorgehensweise setzt eine funktionierende Installation von BaseX mit installiertem TopoX-Plugin voraus.

Bevor TopoX in einem Test verwendet werden kann, muss die Bibliothek mit dem Namen der Datenbank und der Anzahl der verwendeten Datenbanken initialisiert werden:

```XQuery

topox:init-db("MY-DB-000", xs:short(1))
```
Der erste Parameter ("MY-DB-000") ist der Datenbankname.

Datenbanknamen müssen mit einem dreistelligen Index beginnend mit 000 versehen
werden. Dies ist notwendig, da intern das Suffix zur Kodierung von
Referenzen verwendet wird ([Details siehe Architekturdokumentation](Architecture_documentation.md)).

Als zweiter Parameter wird die Anzahl der Datenbanken übergeben.

Die zu untersuchenden Features werden ausgewählt und projiziert. Zum Beispiel mit dieser Query:

```XQuery

let $surfaces := $db/adv:AX_Bestandsdatenauszug/adv:enthaelt/wfsAdv:FeatureCollection/gml:featureMember/*[adv:modellart/adv:AA_Modellart[adv:advStandardModell = $modellart] and local-name() = ('AX_Fliessgewaesser', 'AX_Schiffsverkehr', 'AX_Strassenverkehr') and not(adv:hatDirektUnten) and adv:position/gml:Surface]

```

Mehrere Topologien können für eine Reihe von Features eingerichtet und überprüft
werden. Die Initialisierung eines neuen Topologieobjekts, das als ein topologisches
Thema zusammengefasst wird, erfolgt mit:

```XQuery

let $topoId := topox:new-topology(
'Tatsaechliche_Nutzung',
'/tmp',
$initialEdgeCapacity
)

```

wobei der erste Parameter der Name des Topologie-Themas ist, der zweite ein
Pfad zu einem temporären Ausgabeverzeichnis als Zeichenkette und der dritte
die erwartete Anzahl von Kanten, die in den Daten existieren sollen. Der dritte
Parameter dient dazu, die Datenstruktur in einer geeigneten Größe zu
initialisieren und damit die Performance zu erhöhen. Sie kann kleiner als die
tatsächliche Anzahl angegeben werden, aber dann können interne Umgliederungen
und Leistungsverluste die Folge sein. In Tests wurden gute Ergebnisse mit einem
Wert von `1995000 * Anzahl der Datenbanken` erzielt.

Der Aufruf gibt eine ganze Zahl als Kennung für das topologische Thema
zurück, die in allen weiteren Funktionsaufrufen verwendet werden muss.

Das Parsen und Validieren wird wie folgt gestartet:

```XQuery

let $dummy := topox:parse-surface($surfaces, 'adv:position/gml:Surface', $topoId)

```

Die Features werden im ersten Parameter übergeben. Der zweite Parameter ist
der Pfad zu den Geometrieeigenschaften ausgehend von den übergebenen Features.
Derzeit werden GML LineStringSegmente und Bögen verarbeitet.

Dieser Funktionsaufruf dauert je nach Datenmenge einige Zeit.

Während des Parsen werden Fehler in eine temporäre Datei geschrieben. Zu den
erkannten Fehlern gehören Schnittmengen und überlappende Kanten
(siehe Fehlercodes RING\_OVERLAPPING\_EDGES und RING\_INTERSECTION).
Die Fehler können mit der Funktion topological-errors() abgerufen werden:

```XQuery

let $errors := topox:topological-errors($topoId, ('RING_INTERSECTION'))

for $error in $errors
	let $isFeature := topox:feature($error/IS[1]/text())
	let $isFeatureId := xs:string($isFeature/@gml:id)
order by $isFeatureId
	return $isFeatureId || " : intersection at point " || $error/X/text() || " " $error/Y/text()

```
Die Struktur, die die Funktion topological-errors() zurückgibt, wird im Abschnitt [**Topologische_Fehlerarten**](#topologische-fehlerarten) beschrieben.

Ein komplettes Beispiel finden Sie [hier](../src/test/resources/ddt/queries/default.xq)..


Erweiterte Funktionen
==================

Die folgenden Nachbearbeitungsfunktionen müssen **nach** dem Aufbau der
topologischen Datenstruktur aufgerufen werden ( sprich, nachdem parse-surface()
aufgerufen wurde ).

Erkennung von Löchern
------------------

Löcher - die Oberfläche eines Features mit einer inneren Grenze, die nicht
durch eine andere Oberfläche gefüllt ist - können mit der Funktion
detect-holes() erkannt werden:

```
let $holeCount := topox:detect-holes($topoId)
```

Die Funktion gibt die Anzahl der gefundenen Löcher zurück.
Alle erkannten Probleme werden mit dem Fehlercode HOLE\_EMPTY_INTERIOR
gespeichert und können mit der Funktion topological-errors() abgerufen werden.


Erkennung von freistehenden Oberflächen
-----------------------------------

Freistehende Oberflächen können mit der Funktion detect-free-standing-surfaces()
erkannt werden:

```
let $freeStandingSurfaceCount := topox:detect-free-standing-surfaces($topoId)
```

Die Funktion gibt die Anzahl der gefundenen freistehenden Flächen zurück.

Alle erkannten Probleme werden mit dem Fehlercode FREE\_STANDING\_SURFACE
gespeichert und können mit der Funktion topological-errors() abgerufen werden.

Grenzen prüfen
----------------

Einige Objekte können bestimmte Grenzen beschreiben,
wie z.B. Verwaltungseinheiten, die Bereiche unterteilen. Mit TopoX kann
getestet werden, ob die Grenzen der Verwaltungseinheiten genau an den
Grenzpunkten und Kanten anderer Features liegen.

Grenzen können wie topologische Themen zusammengefasst werden und müssen mit
folgenden Aufruf erstellt werden:

```
let $boundaryId := topox:new-boundary-check($topoId)
```

wobei der Parameter auf ein topologisches Thema als Grundlage für Prüfungen
verweist.

Der zweite Aufruf zum Starten des Parsing von Grenzen ist der
Funktion parse-surface() sehr ähnlich:

```
let $borderParsingDummy := topox:parse-boundary($borders, 'adv:position/gml:*', $boundaryId)
```

Wenn sich die Punkte oder Kanten der Grenzen nicht genau mit den Basisdaten
überschneiden, werden BOUNDARY\_POINT\_DETACHED und BOUNDARY\_EDGE\_INVALID
Fehler in der Fehlerdatei gemeldet.


Issue Map erstellen (experimentell)
----------------

Diese Funktion exportiert eine Map mit den topologischen Fehlern als HTML-Datei
in das temporäre Ausgabeverzeichnis. Dieses Feature ist noch sehr experimentell
und für Entwickler gedacht.

```
let $dummyMap := topox:export-erroneous-features-to-geojson($topoId, "Map")
```


Topologische Fehlerarten
========================

Struktur
---------

TopoX stellt die gefundenen Fehler über zwei Funktionen zur Verfügung:
topological-errors-doc() und topological-errors(). Die erste Funktion
kann verwendet werden, um alle Fehler als einen Knoten abzurufen, die
zweite akzeptiert einen Parameter, um die Fehler nach bestimmten Fehlertypen
zu filtern:

```XQuery

(: Get all erros :)
let $allErrors := topological-errors-doc($topoId)

(: Get only errors of the type RING\_INTERSECTION and the FREE\_STANDING\_SURFACE :)
let $ringAndFreeStandingSurfaceErrors := topological-errors($topoId,
		('RING_INTERSECTION', 'FREE_STANDING_SURFACE'))
```

Die interne Fehlerstruktur, die von der Funktion topological-errors-doc()
vollständig zurückgegeben wird, sieht so aus:

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

Die Wurzel der Struktur ist das Element _TopologicalErrors_ und das Attribut
_name_ repräsentiert das topologische Thema. Unterhalb des Wurzelelements
können die Fehler gefunden werden ( _e_ Elemente). topological-errors()
gibt nur diese (gefilterten) Fehler *e* Elemente zurück.

Jeder Fehler besitzt eine einfache Integer-ID ( _i_ Attribut) und den
Fehlertyp ( _t_ Attribut). Die Namen und Attribute werden abgekürzt,
um ein schnelleres Parsen der Fehlerdatei zu ermöglichen. Ein Fehlercode
kann verschiedene Eigenschaften aufweisen, die im Folgenden beschrieben werden.


Die Eigenschaften X und Y stehen immer für die Koordinaten X und Y. Alle
anderen Eigenschaften (IS, CW, CCW) verweisen auf ein Feature und dessen
Geometrie in der Datenbank. Wenn ein Fehler mehr als einmal für einen Punkt
zurückgegeben wird, wird ein _p_-Attribut (für den vorherigen) zum nächsten
Fehler hinzugefügt, was auf eine Fehlerausblendung hinweisen kann. Der Wert von
_p_ bezieht sich auf das erste Auftreten des Fehlers (_i_ Wert).
topological-errors() gibt diese wiederholten Fehler nicht zurück.

Das Feature kann mit der Funktion `feature()`, die Geometrie mit der Funktion
`geometric-object()` abgefragt werden:

```XQuery

let $error := topox:topological-errors($topoId, ('RING_INTERSECTION'))[1]

(: Feature gml:id :)
let $feature := topox:feature($error/CW[1]/text())
let $featureId := xs:string($feature/@gml:id)

(: Geometry gml:id :)
let $geometry := topox:geometric-object($error/CW[1]/text())
let $geometryId := topox:geometric-object($feature/../../../../@gml:id)

```

Auch wenn mehrere Datenbanken verwendet werden, werden die Objekte
automatisch aus der entsprechenden Datenbank abgefragt.


Codes
-----

### RING\_OVERLAPPING\_EDGES

Es gibt zwei Features, die eine Kante an zwei Punkten definieren. Die
Features liegen auf der gleichen Seite und überlappen sich daher. Die
*IS*-Eigenschaft referenziert das erste Objekt, das sich am Rand befindet,
die *O*-Eigenschaft referenziert das Objekt, das mit dem bestehenden kollidiert. Die
Eigenschaften *X, Y* können sich entweder auf den Anfangs- oder den Endpunkt
der Kante beziehen.

### RING\_INTERSECTION

Es gibt mindestens drei Kanten, die an einem Punkt verbunden sind
(*X, Y* Eigenschaften). Aufgrund ihrer Winkel schneiden sich zwei Kanten im
Verlauf der Linien. Die Eigenschaft *IS* verweist auf das Objekt, bei dem der
Fehler erkannt wurde. *CW* ist das Objekt, das gegen den Uhrzeigersinn mit der
Kante verbunden ist, *CWW* ist das Objekt, das gegen den Uhrzeigersinn mit der
Kante verbunden ist.

### HOLE\_EMPTY\_INTERIOR

Die Oberfläche eines Features mit einer inneren Grenze wird nicht durch
die Oberfläche eines anderen Features gefüllt. Die Eigenschaft *IS*
(und die Eigenschaften *X, Y*) verweist auf das Objekt, bei dem der Fehler
erkannt wurde.

### FREE\_STANDING\_SURFACE

Es gibt mindestens zwei freistehende Oberflächen. Es werden alle Oberflächen
mit Ausnahme der größten, mit den meisten Kanten, gemeldet. Die Eigenschaft
*IS* (und die Eigenschaften *X, Y*) verweist auf das Objekt, bei dem der Fehler erkannt wurde.

### BOUNDARY\_POINT\_DETACHED

Es wurde ein Grenzpunkt (*X, Y* Eigenschaften) definiert, der in den
topologischen Basisdaten nicht gefunden werden konnte. Die Eigenschaft
*IS* verweist auf das Objekt, bei dem der Fehler erkannt wurde.

### BOUNDARY\_EDGE\_INVALID

Es wurde eine Kante (*X, Y* Referenzstartpunkt der Kante, *X2, Y2* Endpunkt)
definiert, die in den topologischen Basisdaten nicht gefunden werden konnte.
Die Eigenschaft *IS* verweist auf das Objekt, bei dem der Fehler erkannt wurde.

### EDGE\_NOT\_FOUND
Eine Kante konnte nicht gefunden werden. Dies ist höchstwahrscheinlich ein
Folgefehler, wenn andere aufgetreten sind und z.B. die Verbindung zwischen
zwei Punkten in der Datenstruktur invalidiert wurde. Wenn dies die
einzige gemeldete Fehlerart ist, dann kann dies auf einen 
Fehler in der Software hinweisen.

### INVALID\_ANGLE
Beim Verbinden einer zusätzlichen Kante mit einem Punkt ist ein Fehler
aufgetreten. Beim Versuch, die Winkel aller verbundenen Kanten zu prüfen,
wurde die maximale Anzahl der möglichen Schritte überschritten. Wenn dies die
einzige gemeldete Fehlerart ist, dann kann dies auf einen 
Fehler in der Software hinweisen.

**Hinweis:** Im Allgemeinen können die Fehler EDGE\_NOT\_FOUND und INVALID\_ANGLE ignoriert werden, wenn Fehler zuvor mit anderen Fehlercodes gemeldet wurden.
