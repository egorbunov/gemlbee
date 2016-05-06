package org.jetbrains.bio.gemlbee

import com.esotericsoftware.yamlbeans.YamlReader
import org.jetbrains.bio.data.DataConfig
import org.jetbrains.bio.ext.toPath
import org.jetbrains.bio.genome.query.GenomeQuery
import java.io.Reader
import java.nio.file.Path
import java.util.*

/**
 * @author Roman.Chernyatchik
 */
class Config(
        /** A genome query, which specifies genome build and chromosome restriction. */
        val genomeQuery: GenomeQuery,

        /** Data tracks (track paired with it's name; name may be empty) */
        val tracks: List<Pair<String, Path>>) {

    companion object {
        val FORMAT = """YAML configuration for genome browser:
genome: <UCSC genome>
tracks:
- [name:] path/to/datafile
-----
${DataConfig.GENOME_DESCRIPTION}
-----
${DataConfig.SUPPORTED_FILE_FORMATS}
- *.tdf for any data
"""

        /**
         * A temporary object for loading weakly-typed YAML data.
         *
         * Turns out it's easier to load a YAML file as a map than to make
         * `yamlbeans` understand how to load our classes.
         *
         * Must be public due to `yamlbeans` design.
         * IMPORTANT: default values are not serialized by `yamlbeans` design!
         */
        class Proxy() {
            @JvmField var genome: String = ""
            @JvmField var tracks: List<Any>? = null
        }


        /** Loads configuration from a YAML file. */
        fun load(reader: YamlReader): Config? {
            val proxy = reader.read(Config.Companion.Proxy::class.java)
            if (proxy != null) {
                val genome = proxy.genome
                require(genome.isNotEmpty()) { "Missing or empty genome" }
                requireNotNull(proxy.tracks) { "Missing or empty tracks" }

                val tracks = proxy.tracks!!.map {
                    if (it is Map<*, *>) {
                        Pair((it.entries.first().key as String).trim(),
                                (it.entries.first().value as String).toPath())
                    } else {
                        Pair("", (it as String).toPath())
                    }
                }

                tracks.filter { !it.first.isEmpty() }.groupBy { it.first }
                        .filter { it.value.size > 1 }.forEach {
                    require(false) { "Track names must be unique, but name [${it.key}] used [${it.value.size}] times"}
                }

                return Config(GenomeQuery.Companion.parse(genome), tracks)
            }
            return null
        }

        /** Loads configuration from a YAML file. */
        fun load(reader: Reader): Config {
            val config = load(YamlReader(reader))
            requireNotNull(config) { "Empty config" }
            return config!!
        }
    }
}