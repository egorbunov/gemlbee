package org.jetbrains.bio.query.desktop

import org.apache.log4j.Logger
import org.jetbrains.bio.browser.query.desktop.DesktopInterpreter
import org.jetbrains.bio.browser.query.desktop.NewTrackViewListener
import org.jetbrains.bio.browser.query.desktop.TrackNameListener
import org.jetbrains.bio.browser.tracks.BigBedTrackView
import org.jetbrains.bio.browser.tracks.LocationsTrackView
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.query.parse.*
import org.jetbrains.bio.query.tracks.FixBinnedArithmeticTrackView
import org.jetbrains.bio.query.tracks.PredicateTrackView
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream


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
    private val newTrackNameListeners = ArrayList<TrackNameListener>()

    // tracks, which were generated
    // view for track created only when show statement evaluated
    private val arithmeticTracks = HashMap<String, ArithmeticTrack>()
    private val predicateTracks = HashMap<String, PredicateTrack>()
    private val trackStatements = HashMap<String, String>()

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

    override fun getAvailableNamedTracks(): List<String> {
        return Stream.concat(predicateTracks.keys.stream(), arithmeticTracks.keys.stream())
                .collect(Collectors.toList<String>())
    }

    /**
     * Interprets query and returns message...
     */
    override fun interpret(query: String): String {
        val parser = LangParser(query, arithmeticTracks, predicateTracks)
        val st = parser.parse()
        var message: String = ""
        when {
            (st is AssignStatement) -> {
                LOG.info("Assign statement parsed. Creating new track with name [${st.id}]")
                val track = st.track
                if (st.id in arithmeticTracks || st.id in predicateTracks) {
                    return "Track with id [${st.id}] already exists!";
                }
                when {
                    (track is ArithmeticTrack) -> {
                        arithmeticTracks.put(st.id, track)
                        message = "New arithmetic (binned) track with name [ ${st.id} ] was created."
                    }
                    (track is PredicateTrack) -> {
                        predicateTracks.put(st.id, track)
                        message = "New predicate (location aware) track with name [ ${st.id} ] was created."
                    }
                    else -> throw IllegalStateException("Interpreter exception")
                }
                trackStatements.put(st.id, query);
                newTrackNameListeners.forEach { it.addTrackName(st.id) }
            }
            (st is ShowTrackStatement) -> {
                LOG.info("Show statement parsed.")
                val track = st.track
                val id: String
                val newTrackView = when {
                    (track is NamedArithmeticTrack) -> {
                        id = track.id
                        FixBinnedArithmeticTrackView(trackStatements[id]!!, track.ref, 50)
                    }
                    (track is NamedPredicateTrack) -> {
                        id = track.id
                        PredicateTrackView(trackStatements[id]!!, track.ref, 50)
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
                throw IllegalStateException("Statement with no effect parsed.")
            }
        }

        return message
    }

    override fun isParseable(query: String): Boolean {
        val parser = LangParser(query, arithmeticTracks, predicateTracks)
        try {
            parser.parse()
            return true
        } catch (e: Exception) {
            return false;
        }
    }

    private fun newTrackViewAdded(view: TrackView) {
        newTrackListeners.forEach { it.addNewTrackView(view) }
    }

    override fun addNewTrackViewListener(listener: NewTrackViewListener) {
        newTrackListeners.add(listener)
    }

    override fun removeNewTrackViewListener(listener: NewTrackViewListener) {
        newTrackListeners.remove(listener)
    }

    override fun addNewTrackNameListener(listener: TrackNameListener) {
        newTrackNameListeners.add(listener)
    }

    override fun removeNewTrackNameListener(listener: TrackNameListener) {
        newTrackNameListeners.remove(listener)
    }
}
