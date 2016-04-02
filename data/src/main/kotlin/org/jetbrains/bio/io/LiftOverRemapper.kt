package org.jetbrains.bio.io

import com.google.common.cache.CacheBuilder
import htsjdk.samtools.liftover.LiftOver
import htsjdk.samtools.liftover.LiftOver.DEFAULT_LIFTOVER_MINMATCH
import htsjdk.samtools.util.Interval
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.checkOrRecalculate
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.ChromosomeNamesMap
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.downloadTo
import org.jetbrains.bio.util.Configuration
import java.nio.file.Path

/**
 * A wrapper around LiftOver parser from htsjdk.
 */
class LiftOverRemapper private constructor(chainPath: Path, targetBuild: String) {

    private val targetChromosomes = ChromosomeNamesMap.create(targetBuild)
    private val liftOver = LiftOver(chainPath.toFile())

    fun remap(range: ChromosomeRange,
              minMachPercentage: Double = DEFAULT_LIFTOVER_MINMATCH): ChromosomeRange? {
        val remapped = liftOver.liftOver(range.toInterval(), minMachPercentage)
        return remapped?.toRange { targetChromosomes[it]!! }
    }

    companion object {
        private val LOG = Logger.getLogger(LiftOverRemapper::class.java)
        private val CACHE = CacheBuilder.newBuilder()
                .build<Pair<String, String>, LiftOverRemapper>()

        operator fun invoke(sourceBuild: String, targetBuild: String): LiftOverRemapper {
            return CACHE.get(sourceBuild to targetBuild) {
                val chain = "${sourceBuild.toLowerCase()}To${targetBuild.capitalize()}.over.chain.gz"
                val path = Configuration.genomesPath / targetBuild / chain
                path.checkOrRecalculate(chain) { output ->
                    val url = "http://hgdownload.cse.ucsc.edu/goldenPath/$sourceBuild/liftOver/$chain";
                    output.let {
                        LOG.info("Downloading $url")
                        url.downloadTo(it)
                    }
                }

                LiftOverRemapper(path, targetBuild)
            }
        }
    }
}

fun Interval.toRange(resolver: (String) -> Chromosome): ChromosomeRange {
    return ChromosomeRange(start - 1, end, resolver(contig))
}

fun ChromosomeRange.toInterval(): Interval {
    return Interval(chromosome.name, startOffset + 1, endOffset, false, null)
}