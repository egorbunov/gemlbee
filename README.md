JetBrains BioLabs GeMLBee
=========================

This is a compact version of gemlbee genome browser and dependencies.

Useful links
------------

* Our [homepage](https://research.jetbrains.org/groups/biolabs)
* Our [wiki](http://biolabs.intellij.net)
* Our NIH powered [genome browser](http://genomebrowser.labs.intellij.net)

Setup
-----

We use a project-wide property file to configure locations of genomes and various data sets.
Copy and modify the properties in the example file.

    ```bash
    $ cp config.properties.example config.properties
    $ cat config.properties
    genomes.path=/path/to/data/genomes
    raw.data.path=/path/to/data/raw-data
    experiments.path=/path/to/data/experiments
    ```

This fork additions
--------------------

* Add `query` module with code for parsing track manipulation queries
* Add interpreter for such queries for desktop version of the browser to `browser-tracks` module
* Add two classes, inherited from `TrackView`, for visualizing query-generated tracks:
  one for location aware tracks (predicate) and another for binned tracks (with fix bin number on
  observed chromosome range)
* Change `SearchPanel`, `BrowserModel` a little to handle queries, not only chromosome ranges
* Change few classes to make runtime generated tracks addition possible
* Change autocompletion and add syntax highlighting for queries

Available operations with tracks
--------------------------------

First I need to say, that only BigBedTrack's supported for now. 
Second, numbers are also treated as binned tracks: `1` -> `1 | 1 | 1 | ... | 1 |` (every bin filled with number). And so arithmetic operations are all made over vectors, element vise.

Next operations result to binned tracks:

* Arithmetic operations `TRACK (+|-|*|/) TRACK` of any complexity. Operands may be rational numbers or tracks. Track identifiers are used to operate with tracks.
* If-operator: `if PREDICATE then TRACK else TRACK`. If-operator may be used in more complex queries like: `x * (if () then () else ()) + (if () then () else ())`. Condition in if statement is predicate track (see below)

Next operations result to predicate tracks:

* `TRUE`, `FALSE` -- primitive predicates
* `TRACK (< | > | <= | >= | == | !=) TRACK` -- element vise relation operations
* `PREDICATE (and | or) PREDICATE`
* `not PREDICATE`

Operations described above has no real effect in browser as long as result of one of such operations won't be named:

* `track_name := TRACK_EXPRESSION` -- assigning track

After evaluating that command browser will store new track (and add it to auto completion), but not it's view. To add view of that track to track view component use `show` command:

* `show TRACK_NAME` -- adds track view to track view list component

Both generated predicate tracks and binned tracks can be visualized (and so named too), but predicate tracks are location aware.

TODO:
-----

* ~~Fix query handling in `SearchPanel` class~~
* ~~Add autocompletion for queries~~
* Make new track views addition more stable (sometimes need to zoom in / zoom out to get track visible)
