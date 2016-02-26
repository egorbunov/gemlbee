package org.jetbrains.bio.io

import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntObjectHashMap
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.ChromosomeNamesMap
import org.jetbrains.bio.genome.ScoredRange
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.containers.GenomeMap
import org.jetbrains.bio.genome.containers.GenomeStrandMap
import org.jetbrains.bio.genome.containers.genomeMap
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.util.Colors
import org.jetbrains.bio.util.Progress
import java.awt.Color
import java.io.IOException
import java.nio.file.Path
import java.util.*

operator private fun <T> TIntObjectMap<T>.contains(key: Int): Boolean {
    return containsKey(key)
}

/**
 * A container for coloured BED data.
 *
 * @author Oleg Shpynov
 * @since 30/01/15
 */
class ColoredBed(val scoredMap: GenomeMap<MutableList<ScoredRange>>,
                        val colors: TIntObjectMap<Color>,
                        val labels: TIntObjectMap<String>) {

    companion object {
        @JvmStatic fun load(genomeQuery: GenomeQuery,
                            inputQuery: InputQuery<Iterable<BedEntry>>): ColoredBed {
            val chromosomes = ChromosomeNamesMap.create(genomeQuery)
            val colorsMap = TIntObjectHashMap<Color>()
            val statesMap = TIntObjectHashMap<String>()
            val colorSet = HashSet<Color>()
            val scoredMap = genomeMap<MutableList<ScoredRange>>(genomeQuery) { ArrayList() }
            for (entry in inputQuery.get()) {
                if (entry.chromosome !in chromosomes) {
                    continue
                }

                val state = entry.score
                if (state !in colorsMap) {
                    // Use slightly different colors to differ them in states_distribution.r script,
                    // Otherwise several states are composed together.
                    var color = entry.itemRgb ?: Color.BLACK
                    val rgb = intArrayOf(color.red, color.green, color.blue)
                    var component = 0
                    while (color in colorSet) {
                        if (rgb[component] < 255) {
                            rgb[component]++
                        } else {
                            rgb[component]--
                        }
                        component = (component + 1) % 3
                        color = Color(rgb[0], rgb[1], rgb[2])
                    }
                    colorSet.add(color)
                    colorsMap.put(state, color)
                }

                if (state !in statesMap) {
                    statesMap.put(state, entry.name)
                }

                val chromosome = chromosomes[entry.chromosome] ?: continue
                scoredMap[chromosome].add(
                        ScoredRange(entry.chromStart, entry.chromEnd, state.toDouble()))
            }

            for (chromosome in genomeQuery.get()) {
                if (chromosome.name in chromosomes) {
                    Collections.sort(scoredMap[chromosome])
                }
            }

            return ColoredBed(scoredMap, colorsMap, statesMap)
        }

        /**
         * Save batch of scored ranges map
         */
        @Throws(Exception::class)
        @JvmStatic fun save(scoredRanges: Map<CellId, GenomeStrandMap<MutableList<ScoredRange>>>,
                            path: Path, cellIds: Collection<CellId>,
                            genomeQuery: GenomeQuery, states: Int) {

            val progress = Progress.builder()
                    .title("Saving as colored bed tracks")
                    .incremental(cellIds.size.toLong())
            for (cellId in cellIds) {
                // Now that we have ScoredRanges for each cell line we can save it to bed file
                save(genomeQuery, states, scoredRanges[cellId]!!,
                     path / "${cellId.name}.bed.gz")
                progress.report()
            }
            progress.done()
        }

        /**
         * Saves scored map to colored bed format
         */
        @Throws(IOException::class)
        @JvmStatic fun save(genomeQuery: GenomeQuery, states: Int,
                            scoredRanges: GenomeStrandMap<MutableList<ScoredRange>>,
                            bedPath: Path) {

            val stateLabels = (0 until states).map { it.toString() }
            BedFormat.DEFAULT.print(bedPath).use { printer ->
                printer.print("track name=mapping description=\"mapping\" useScore=1 itemRGB=\"On\"")
                val palette = Colors.palette(states)
                for (chromosome in genomeQuery.get()) {
                    for (range in scoredRanges[chromosome, Strand.PLUS]) {
                        val state = range.score.toInt()
                        printer.print(BedEntry(chromosome.name, range.startOffset, range.endOffset,
                                               stateLabels[state], state, Strand.PLUS.char,
                                               0, 0, palette[state], 0, IntArray(0), IntArray(0)))
                    }
                }
            }
        }
    }
}
