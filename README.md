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
