package org.jetbrains.bio.histones

import org.jetbrains.bio.ext.exists
import org.jetbrains.bio.ext.isRegularFile
import org.jetbrains.bio.ext.stem
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.io.BedFormat
import org.jetbrains.bio.io.BedParser
import java.nio.file.Path

class BedTrackQuery(val genomeQuery: GenomeQuery, val path: Path,
                    val format: BedFormat? = null) :
        InputQuery<Iterable<BedEntry>> {

    override val id: String get() = path.stem

    override val description: String get() = "Bed track $path"

    override fun toString() = description

    override fun getUncached(): BedParser {
        require(path.exists && path.isRegularFile) { "Bad file: $path" }
        return (format ?: BedFormat.auto(path)).parse(path)
    }
}
