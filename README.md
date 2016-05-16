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

This fork additions:
--------------------

* Add `query` module with code for parsing track manipulation queries
* Add interpreter for such queries for desktop version of the browser to `browser-tracks` module
* Add two classes, inherited from `TrackView`, for visualizing query-generated tracks:
  one for location aware tracks (predicate) and another for binned tracks (with fix bin number on
  observed chromosome range)
* Change `SearchPanel`, `BrowserModel` a little to handle queries, not only chromosome ranges
* Change few classes to make runtime generated tracks addition possible


TODO:
-----

* Fix query handling in `SearchPanel` class
* Add autocompletion for queries
* Make new track views addition more stable
