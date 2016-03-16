package org.jetbrains.bio.gemlbee

import com.esotericsoftware.yamlbeans.YamlReader
import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.webapp.WebAppContext
import org.jetbrains.bio.browser.LociCompletion
import org.jetbrains.bio.browser.desktop.BrowserSplash
import org.jetbrains.bio.browser.desktop.DesktopGenomeBrowser
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.*
import org.jetbrains.bio.browser.web.Browsers
import org.jetbrains.bio.browser.web.Handlers
import org.jetbrains.bio.browser.web.ServerUtil
import org.jetbrains.bio.data.DataConfig
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.histones.BedTrackQuery
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.io.BedFormat
import org.jetbrains.bio.io.LiftOverRemapper
import org.jetbrains.bio.methylome.CytosineContext
import org.jetbrains.bio.methylome.MethylomeQuery
import org.jetbrains.bio.transcriptome.KallistoQuery
import org.jetbrains.bio.transcriptome.fastqReads
import org.jetbrains.bio.util.Configuration
import org.jetbrains.bio.util.Logs
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable

/**
 * @author Roman.Chernyatchik
 * @author Oleg Shpynov
 */
open class GeMLBeeCLA {
    companion object {
        private val LOG = Logger.getLogger(GeMLBeeCLA::class.java)

        @JvmStatic fun main(args: Array<String>) {
            with(OptionParser()) {
                accepts("server", "execute in web server mode at http://localhost:port")
                acceptsAll(listOf("p", "port"), "port to start web server")
                        .withRequiredArg()
                        .ofType(Int::class.java)
                        .defaultsTo(8080)

                // Tracks config file
                acceptsAll(listOf("i", "input"), Config.FORMAT)
                        .withRequiredArg()
                        .required()
                        .withValuesConvertedBy(PathConverter.exists())

                acceptsAll(listOf("m", "markup"), DataConfig.MARKUP)
                        .withRequiredArg()
                        .withValuesConvertedBy(PathConverter.noCheck())
                        .defaultsTo(System.getProperty("user.home").toPath() / ".gemlbee/genomes")

                acceptsAll(listOf("l", "loci"), """
Comma separated list of paths to bed files with custom loci of interest information.
Bed file columns are chromosome, start offset, end offset, name, strand.
These loci sets will be available in browser autocompletion for file names.

Example: loci.bed
-----------------------------------------
chr6    122655582   122665795   NANOG   +
chr17   35640983    35649721    POU5F1  +
chr8    12393518    12401554    SOX1    +
chr4    55538009    55547347    KLF4    -
-----------------------------------------
""")
                        .withRequiredArg()
                        .withValuesSeparatedBy(',')
                        .withValuesConvertedBy(PathConverter.exists())


                // Logging level:
                acceptsAll(listOf("d", "debug"), "print all the debug info")

                formatHelpWith(BuiltinHelpFormatter(200, 2))

                parse(args) { options ->
                    if ("debug" in options) {
                        Logs.addConsoleAppender(Level.TRACE)
                        LOG.debug("Debug enabled")
                    } else {
                        Logs.addConsoleAppender(Level.INFO)
                    }

                    val serverMode = options.has("server")

                    // "Configuration" singleton rewrites properties on startup if config.properties file
                    // is available so, let's load it explicitly before applying cmdline options
                    // in order to be able to launch browser locally from sources
                    val pathsConfig = Configuration

                    // Now let's apply cmd line options:
                    val properties = System.getProperties()

                    val launchedFromSrc = properties.contains("genomes.path")
                    if (options.hasArgument("markup") || !launchedFromSrc) {
                        val markupPath = (options.valueOf("markup") as Path).createDirectories()
                        properties.setProperty("genomes.path", markupPath.toString())
                    }
                    LOG.info("Markup folder ${pathsConfig.genomesPath}")

                    // Use ~/.gemlbee for caches
                    val cachesFolder = (properties.getProperty("user.home") / ".gemlbee").createDirectories()
                    properties.setProperty("experiments.path", cachesFolder.toAbsolutePath().toString())
                    LOG.info("Caches directory ${pathsConfig.experimentsPath}")

                    val port = options.valueOf("port") as Int
                    if (serverMode) {
                        // Check if port is available
                        Logs.checkOrFail(ServerUtil.isPortAvailable(port), "Port $port is not available.");
                    } else {
                        BrowserSplash.display()
                    }

                    // Config file:
                    val input = options.valueOf("input") as Path
                    LOG.info("Loading config from $input")

                    @Suppress("UNCHECKED_CAST")
                    val lociPaths = options.valuesOf("loci") as List<Path>
                    try {
                        GeMLBeeCLA().launch(loadConfigs(input), lociPaths, serverMode, port)
                    } catch (e: ConfigParsingException) {
                        fail(e.message!!)
                    } catch (e: YamlReader.YamlReaderException) {
                        fail(e.message!!)
                    }

                }
            }
        }

        private fun OptionParser.loadConfigs(input: Path): ArrayList<Config> {
            var configs = arrayListOf<Config>()
            input.bufferedReader().use {
                val yamlReader = YamlReader(it)
                while (true) {
                    val config = Config.load(yamlReader) ?: break
                    configs.add(config)
                }
            }
            if (configs.isEmpty()) {
                fail("Empty config")
            }
            val species = configs.first().genomeQuery.genome.species
            if (!configs.all { species == it.genomeQuery.genome.species }) {
                fail("Different species are not supported")
            }
            val builds = configs.map { it.genomeQuery.build }.distinct().sorted()
            if (builds.size > 1) {
                val master = configs.first().genomeQuery.build
                LOG.info("Master genome: $master")
                LOG.info("All genomes found: $builds")
                for (b in builds) {
                    if (b != master) {
                        LOG.info("Loading liftover $master -> $b")
                        LiftOverRemapper(master, b)
                    }
                }
            }
            return configs
        }
    }

    private fun launch(configs: List<Config>, lociPaths: List<Path>, serverMode: Boolean, port: Int) {
        val master = configs.first().genomeQuery

        LOG.info("Processing genomic markup tracks")
        val tracks = arrayListOf(GenesTrackView(), CpGITrackView(), RepeatsTrackView(), SequenceTrack())

        val completionGroups = HashMap(LociCompletion.DEFAULT_COMPLETION)
        parseCustomLoci(master, lociPaths, completionGroups)

        val model = if (lociPaths.isEmpty()) {
            SingleLocationBrowserModel(master)
        } else {
            LOG.info("Parsing loci to show")
            val fstPath = lociPaths.first()
            MultipleLocationsBrowserModel.create(fstPath.name,
                                                 completionGroups[fstPath.name]!!,
                                                 SingleLocationBrowserModel(master))
        }

        LOG.info("Processing configuration data tracks")
        configureTracks(configs, master, tracks)

        LOG.info("Starting browser..")
        if (serverMode) {
            serverMode(HeadlessGenomeBrowser(model, tracks, completionGroups), port)
        } else {
            DesktopGenomeBrowser(model, tracks, completionGroups).show()
        }
    }

    fun parseCustomLoci(genomeQuery: GenomeQuery,
                               lociPaths: List<Path>,
                               completionGroups: HashMap<String, (GenomeQuery) -> List<LocationReference>>) {

        for (path in lociPaths) {
            val loci = arrayListOf<Pair<String, Location>>()
            val bedFormat = BedFormat(BedEntry.CHROMOSOME,
                    BedEntry.START_POS, BedEntry.END_POS,
                    BedEntry.NAME, BedEntry.STRAND)

            val chromosomes = ChromosomeNamesMap.create(genomeQuery)
            bedFormat.parse(path).use { parser ->
                for (e in parser) {
                    val chromosome = chromosomes[e.chromosome] ?: continue
                    loci.add(e.name to Location(e.chromStart, e.chromEnd,
                            chromosome, e.strand.toStrand()))
                }
            }
            // Resolves locus name as gene, if possible. This will be useful for better
            // genes track view rendering in multiple locations mode
            val locFun: (GenomeQuery) -> List<LocationReference> = {
                loci.map {
                    val (name, loc) = it
                    val gene = GeneResolver.get(genomeQuery.build, name).singleOrNull()
                    if (gene != null) {
                        GeneLocRef(gene, loc)
                    } else {
                        // some named locus
                        NamedLocRef(name, loc)
                    }
                }
            }
            val fileName = path.name
            assert(fileName !in completionGroups) { "Duplicated file name $fileName" }
            completionGroups[fileName] = locFun
        }
    }

    private fun serverMode(browser: HeadlessGenomeBrowser, port: Int) {
        Browsers.registerBrowser("", Callable { browser })

        val handlers = HandlerList()
        handlers.addHandler(Handlers.createFullLogHandler())
        handlers.addHandler(Handlers.createWebLogHandler())
        handlers.addHandler(Handlers.createAPIHandler())

        val contextHandler = WebAppContext(null, "/")
        contextHandler.resourceBase = ServerUtil.getWebAppResourceBase()
        handlers.addHandler(contextHandler)

        // Start server
        ServerUtil.startServer(port, handlers)
    }

    fun configureTracks(configs: List<Config>, master: GenomeQuery, tracks: ArrayList<TrackView>) {
        for (config in configs) {
            for (file in config.tracks) {
                val fName = file.name
                val gq = config.genomeQuery
                when {
                    fName.endsWith(".bed") || fName.endsWith(".bed.zip") || fName.endsWith(".bed.gz") ->
                        addChipSeq(master, gq, file, tracks)

                    fName.endsWith(".bam") -> addBsSeq(fName, master, gq, file, tracks)

                    fName.endsWith(".fastq") || fName.endsWith(".fastq.gz") ->
                        addRnaSeq(fName, master, gq, tracks, arrayOf(file))

                    fName.endsWith(".tdf") -> addTdf(file, master, gq, tracks)
                    fName.endsWith(".bb") -> addBigBed(file, master, gq, tracks)

                    // as fastq reads folder:
                    file.isDirectory -> addRnaSeq(fName, master, gq, tracks, file.fastqReads)
                    else -> unsupportedError(file)
                }
            }
        }
    }

    protected open fun addTdf(file: Path, master: GenomeQuery, gq: GenomeQuery, tracks: MutableList<TrackView>) {
        tracks.add(wrap(master, gq, TdfTrackView(file)))
    }

    protected open fun addBigBed(file: Path, master: GenomeQuery, gq: GenomeQuery, tracks: MutableList<TrackView>) {
        tracks.add(wrap(master, gq, BigBedTrackView(file, 100)))
    }

    protected open fun addRnaSeq(condition: String,
                                 master: GenomeQuery,
                                 gq: GenomeQuery,
                                 tracks: MutableList<TrackView>,
                                 fastqReads: Array<Path>) {
        val query = KallistoQuery(gq.genome, CellId(condition, ""), fastqReads = fastqReads)
        val trackView = KallistoTrackView(query)
        tracks.add(wrap(master, gq, trackView))
    }

    protected open fun addBsSeq(condition: String,
                                master: GenomeQuery,
                                gq: GenomeQuery,
                                trackPath: Path,
                                tracks: MutableList<TrackView>) {
        val query = MethylomeQuery.forFile(gq, condition, trackPath, verboseDescription = true)
        val trackView = MethylomeRawDataTrackView(query, CytosineContext.CG, 50)
        tracks.add(wrap(master, gq, trackView))
    }

    protected open fun addChipSeq(master: GenomeQuery, gq: GenomeQuery, trackPath: Path, tracks: MutableList<TrackView>) {
        val query = BedTrackQuery(gq, trackPath)
        tracks.add(wrap(master, gq, BedCovTrackBinnedView(query)))
    }

    private fun wrap(master: GenomeQuery, gq: GenomeQuery, trackView: TrackView) =
            if (master == gq) {
                trackView
            } else {
                LiftOverTrackView(trackView, master, gq)
            }

    protected open fun unsupportedError(trackPath: Path) {
        throw ConfigParsingException("Unknown file type: ${trackPath.toAbsolutePath()}")
    }
}


data class NamedLocRef(override val name:String, override val location: Location) : LocationReference {
    val metaData: Any?
        get() = null;

    override fun update(newLoc: Location) = NamedLocRef(name, newLoc)
}
