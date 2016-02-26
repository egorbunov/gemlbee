package org.jetbrains.bio.histones

import org.jetbrains.bio.ext.exists
import org.jetbrains.bio.ext.isRegularFile
import org.jetbrains.bio.ext.stem
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.io.BedFormat
import java.nio.file.Path

class BedTrackQuery(val genomeQuery: GenomeQuery, val path: Path,
                    val format: BedFormat = BedFormat.auto(path)) :
        InputQuery<Iterable<BedEntry>> {

    init {
        require(path.exists && path.isRegularFile) { "Bad file: $path" }
    }

    override val id: String get() = path.stem

    override val description: String get() = "Bed track $path"

    override fun toString() = description

    override fun getUncached() = format.parse(path)
}
