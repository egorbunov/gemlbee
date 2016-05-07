package org.jetbrains.bio.query

import org.apache.log4j.Logger
import org.jetbrains.bio.browser.tracks.BigBedTrackView
import org.jetbrains.bio.browser.tracks.LocationAwareTrackView
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.query.parse.*
import org.jetbrains.bio.query.tracks.ArithmeticTrackView
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
class DesktopInterpreter(aliasedTrackViews: HashMap<String, TrackView>) {
    private val LOG = Logger.getLogger(DesktopInterpreter::class.java)
    private val newTrackListeners = ArrayList<NewTrackViewListener>()
    private val trackViewsAliases = HashMap<TrackView, String>()

    // tracks, which were generated
    // view for track created only when show statement evaluated
    private val arithmeticTracks = HashMap<String, ArithmeticTrack>()
    private val predicateTracks = HashMap<String, PredicateTrack>()

    init {
        // Working only with BigBedTrackView for now
        aliasedTrackViews.filter { (it.value is BigBedTrackView || it is LocationAwareTrackView<*>)
                && !it.key.isEmpty() }.forEach { trackViewsAliases.put(it.value, it.key) }

        trackViewsAliases.forEach { view, name ->
            when {
                (view is BigBedTrackView) -> arithmeticTracks[name] = BigBedFileTrack(name, view.bbf)
                // TODO: add LocationsTrackView support here
//                (view is LocationsTrackView) -> predicateTracks[name] = PredicateTrackView()
            }
        }
    }

    fun interpret(query: String) {
        val parser = LangParser(query, arithmeticTracks, predicateTracks)

        val st = parser.parse()
        when {
            (st is AssignStatement) -> {
                LOG.info("Assign statement was parsed: [ ${st.id} := ... ]")
                val track = st.track
                when {
                    (track is ArithmeticTrack) -> arithmeticTracks.put(st.id, track)
                    (track is PredicateTrack) -> predicateTracks.put(st.id, track)
                    // TODO: change exception
                    else -> throw IllegalArgumentException("Interpreter exception")
                }
            }
            (st is ShowTrackStatement) -> {
                LOG.info("Show statement was parsed...")
                val track = st.track
                var id: String // TODO: don't like this...
                val newTrackView = when {
                    (track is NamedArithmeticTrack) -> {
                        id = track.id
                        ArithmeticTrackView(track.id, track.ref)
                    }
                    (track is NamedPredicateTrack) -> {
                        id = track.id
                        PredicateTrackView(track.id, track.ref)
                    }
                    else -> throw IllegalArgumentException("Interpreter exception!") // TODO: err handling
                }
                trackViewsAliases.put(newTrackView, id)
                newTrackViewAdded(newTrackView)
            }
            else -> {
                // TODO: Log...
            }
        }

    }

    fun getAliasForView(trackView: TrackView): String? {
        return trackViewsAliases[trackView]
    }

    fun newTrackViewAdded(view: TrackView) {
        newTrackListeners.forEach { it.addNewTrackView(view) }
    }

    fun addNewTrackListener(listener: NewTrackViewListener) {
        newTrackListeners.add(listener)
    }

    fun removeNewTrackListener(listener: NewTrackViewListener) {
        newTrackListeners.remove(listener)
    }
}
