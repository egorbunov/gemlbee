package org.jetbrains.bio.browser.tracks

import com.google.common.cache.CacheBuilder
import com.google.common.collect.ArrayListMultimap
import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.transcriptome.TranscriptAbundance
import java.awt.Color
import java.awt.Graphics

/**
 * @author Sergei Lebedev
 * @since 09/09/15
 */
public class KallistoTrackView(private val inputQuery: InputQuery<List<TranscriptAbundance>>) :
        TrackView(inputQuery.id) {

    private val ABUNDANCES = CacheBuilder.newBuilder().weakKeys().build<Chromosome, List<TranscriptAbundance>>()

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val trackWidth = conf[TrackView.WIDTH]
        val chromosome = model.chromosome
        val range = model.range

        val locRef = model.rangeMetaInf
        val locTranscript: Gene? = if (locRef is GeneLocRef) locRef.gene else null

        val levels = ArrayListMultimap.create<Int, Range>()
        for ((transcript, tpm) in ABUNDANCES[chromosome,
                {inputQuery.get().filter { it.transcript.chromosome == chromosome }}]) {
            val location = transcript.location.toRange()
            if (location intersects range) {
                // XXX: if locTranscript is known perhaps do not paint other isoforms
                var i = 0
                while (i < levels.size()) {
                    if (levels[i].none { location intersects it }) {
                        break
                    }
                    i++
                }

                levels.put(i, location)

                val start = genomeToScreen(location.startOffset, trackWidth, range)
                val end = genomeToScreen(location.endOffset, trackWidth, range)

                g.color = if (locTranscript != null && locTranscript != transcript) {
                    Color.LIGHT_GRAY
                } else {
                    Color((Math.min(tpm / 10, 1.0) * 255).toInt(), 0, 0)
                }
                val vOffset = i * 7

                g.fillRect(start, vOffset + 2, Math.max(1, end - start), 2)
            }
        }
    }

    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                                    Color.BLACK to "not expressed",
                                    Color.RED to "expressed")
    }
}
