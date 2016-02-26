package org.jetbrains.bio.browser.model

import org.jetbrains.bio.ext.collectHack
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.genome.ChromosomeNamesMap
import org.jetbrains.bio.genome.LocationAware
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * @author Oleg Shpynov
 */
open class MultipleLocationsBrowserModel protected constructor(
        val id: String,
        val originalModel: BrowserModel,
        val locationReferences: List<LocationReference>,
        protected val cumulativeLength: IntArray =
            MultipleLocationsBrowserModel.cumulativeLength(locationReferences, id, originalModel.genomeQuery),
        private val initRange: Range =
            MultipleLocationsBrowserModel.defaultRange(cumulativeLength))
: BrowserModel(originalModel.genomeQuery, initRange) {

    override val length: Int
        get() = cumulativeLength.last()

    companion object {
        const val MAX_LOCATIONS: Long = 10000
        private val INIT_LOCATIONS_PER_SCREEN: Int = 20

        @JvmStatic fun create(id: String,
                              locF: (GenomeQuery) -> List<LocationReference>,
                              model: BrowserModel): MultipleLocationsBrowserModel
                = MultipleLocationsBrowserModel(id, model,
                                                filter(locF(model.genomeQuery).stream(), model.genomeQuery))

        private fun cumulativeLength(locations: List<LocationAware>, id: String, gq: GenomeQuery): IntArray {
            check(locations.isNotEmpty()) {
                "No locations available for '$id' on ${gq.getShortNameWithChromosomes()}"
            }
            return locations.stream().mapToInt { it.location.length() }.toArray()
                    .let { Arrays.parallelPrefix(it) { a, b -> a + b }; it }
        }

        /**
         * We don't want default scale to be too small, so that we show max [INIT_LOCATIONS_PER_SCREEN] locations
         */
        private fun defaultRange(cumulativeLength: IntArray)
                = Range(0, cumulativeLength[Math.min(cumulativeLength.size, INIT_LOCATIONS_PER_SCREEN) - 1])

        fun filter(locStream: Stream<LocationReference>, query: GenomeQuery): List<LocationReference> {
            // Filter only locations on genome query
            val chrNamesMap = ChromosomeNamesMap.create(query)
            return locStream.filter { it.location.length() > 0 }
                    .filter { chrNamesMap.contains(it.location.chromosome.name) }
                    .distinct().sorted { o1, o2 -> o1.location.compareTo(o2.location) }
                    .limit(MultipleLocationsBrowserModel.MAX_LOCATIONS)
                    .collectHack(Collectors.toList())
        }
    }

    override fun presentableName() = "$id:${range.startOffset}-${range.endOffset}"

    fun visibleLocations(): List<LocationReference> {
        val visibleStart = range.startOffset
        val visibleEnd = range.endOffset

        val (startIndex, endIndex ) = visibleLocsStartEndIndexes()
        val visibleLocationsIndexes
                = IntStream.range(startIndex,
                                  startIndex + Math.max(1, endIndex - startIndex + 1))
        val visibleLocs = visibleLocationsIndexes.mapToObj { i -> locationReferences[i] }
                .collectHack(Collectors.toList())

        // First and last location may be partly visible in current range, so let's cut them off:

        // Correct first:
        val first = visibleLocs[0]
        val firstLoc = first.location
        val dStart = if (startIndex > 0) visibleStart - cumulativeLength[startIndex - 1] else visibleStart
        visibleLocs[0] = first.update(firstLoc.copy(startOffset = firstLoc.startOffset + dStart))

        // Correct last:
        val last = visibleLocs[visibleLocs.size - 1]
        val lastLoc = last.location
        val dEnd = visibleEnd - cumulativeLength[endIndex]
        visibleLocs[visibleLocs.size - 1] = last.update(lastLoc.copy(endOffset = lastLoc.endOffset + dEnd))

        return visibleLocs
    }

    private fun visibleLocsStartEndIndexes(range: Range = this.range): Pair<Int, Int> {
        val visibleStart = range.startOffset
        val visibleEnd = range.endOffset

        val startIndex = index(visibleStart + 1)
        // Check start index
        check(visibleStart < cumulativeLength[startIndex])
        check(startIndex == 0 || visibleStart >= cumulativeLength[startIndex - 1])

        val endIndex = index(visibleEnd)
        // Check end index
        check(endIndex == 0 || visibleEnd > cumulativeLength[endIndex - 1])
        check(visibleEnd <= cumulativeLength[endIndex])

        return startIndex.to(endIndex)
    }

    private fun index(offset: Int) = Arrays.binarySearch(cumulativeLength, offset)
            .let { if (it < 0) it.inv() else it }

    override fun copy(): MultipleLocationsBrowserModel
            = MultipleLocationsBrowserModel(id, originalModel, locationReferences, cumulativeLength, range)

}
