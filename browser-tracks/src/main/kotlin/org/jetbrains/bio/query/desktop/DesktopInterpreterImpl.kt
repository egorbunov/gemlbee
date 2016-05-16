package org.jetbrains.bio.query.desktop

import org.apache.log4j.Logger
import org.jetbrains.bio.browser.query.desktop.DesktopInterpreter
import org.jetbrains.bio.browser.query.desktop.NewTrackViewListener
import org.jetbrains.bio.browser.tracks.BigBedTrackView
import org.jetbrains.bio.browser.tracks.LocationsTrackView
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.query.parse.*
import org.jetbrains.bio.query.tracks.FixBinnedArithmeticTrackView
import org.jetbrains.bio.query.tracks.PredicateTrackView
import java.util.*


/**
 * @author Egor Gorbunov
 * @since 06.05.16
 */


/**
 * Queries interpreter. It is `Desktop` specific because there are queries,
 * whose evaluation affects the view.
 */
class DesktopInterpreterImpl(trackViews: List<TrackView>): DesktopInterpreter {
    private val LOG = Logger.getLogger(DesktopInterpreterImpl::class.java)

    private val newTrackListeners = ArrayList<NewTrackViewListener>()

    // tracks, which were generated
    // view for track created only when show statement evaluated
    private val arithmeticTracks = HashMap<String, ArithmeticTrack>()
    private val predicateTracks = HashMap<String, PredicateTrack>()

    init {
        // Working only with BigBedTrackView for now
        trackViews.filter { !it.alias.isEmpty() }.forEach { view ->
            when {
                (view is BigBedTrackView) ->  {
                    arithmeticTracks[view.alias] = BigBedFileTrack(view.alias, view.bbf)
                }
                (view is LocationsTrackView) -> {
                    predicateTracks[view.alias] = throw IllegalArgumentException("=(")
                }
            }
        }
    }

    /**
     * Interprets query and returns message...
     */
    override fun interpret(query: String): String {
        val parser = LangParser(query, arithmeticTracks, predicateTracks)

        val st = parser.parse()
        when {
            (st is AssignStatement) -> {
                LOG.info("Assign statement parsed. Creating new track with name [${st.id}]")
                val track = st.track
                when {
                    (track is ArithmeticTrack) -> {
                        arithmeticTracks.put(st.id, track)
                        return "New arithmetic (binned) track with name [ ${st.id} ] was created."
                    }
                    (track is PredicateTrack) -> {
                        predicateTracks.put(st.id, track)
                        return "New predicate (location aware) track with name [ ${st.id} ] was created."
                    }
                    else -> throw IllegalStateException("Interpreter exception")
                }
            }
            (st is ShowTrackStatement) -> {
                LOG.info("Show statement parsed.")
                val track = st.track
                var id: String
                val newTrackView = when {
                    (track is NamedArithmeticTrack) -> {
                        id = track.id
                        FixBinnedArithmeticTrackView(track.id, track.ref)
                    }
                    (track is NamedPredicateTrack) -> {
                        id = track.id
                        PredicateTrackView(track.id, track.ref)
                    }
                    else -> {
                        throw IllegalStateException("Interpreter exception!")
                    }
                }
                newTrackView.alias = id
                newTrackViewAdded(newTrackView)
            }
            else -> {
                LOG.info("Statement with no effect parsed.")
                return "Statement has no effect";
            }
        }

        return ""
    }

    private fun newTrackViewAdded(view: TrackView) {
        newTrackListeners.forEach { it.addNewTrackView(view) }
    }

    override fun addNewTrackListener(listener: NewTrackViewListener) {
        newTrackListeners.add(listener)
    }

    override fun removeNewTrackListener(listener: NewTrackViewListener) {
        newTrackListeners.remove(listener)
    }
}
