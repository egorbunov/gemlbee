package org.jetbrains.bio.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.jetbrains.bio.genome.*;
import org.jetbrains.bio.genome.sequence.Nucleotide;
import org.jetbrains.bio.genome.sequence.TwoBitWriter;
import org.jetbrains.bio.sampling.Sampling;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author Oleg Shpynov
 * @since 20.7.12
 */

public class TestOrganismDataGenerator {
    private static final Logger LOG = Logger.getLogger(TestOrganismDataGenerator.class);
    private static final Random RANDOM = new Random();

    private final static Map<String, Integer> CHROMOSOMES = ImmutableMap.of(
            "chr1", (int) 1e7,
            "chr2", (int) 1e6,
            "chr3", (int) 1e6,
            "chrX", (int) 1e6,
            "chrM", (int) 1e5
    );

    private static final int MAX_GAPS_IN_CHR = 10;
    private static final int MAX_GAP_LENGTH_BP = 100;

    public static void main(final String[] args) throws IOException {
        final Genome genome = Genome.get("to1");

        final Path configPath = Paths.get("config.properties");
        if (Files.notExists(configPath)) {
            final Properties properties = new Properties();
            properties.setProperty(
                    "genomes.path", Files.createTempDirectory(genome.getBuild()).toString());

            try (OutputStream os = Files.newOutputStream(configPath)) {
                properties.store(os, genome.getBuild());
            }
        }

        createSequences(genome);
        createAnnotations(genome);
        createGaps(genome);
        createCytoBands(genome);
    }


    private static void createSequences(final Genome genome) throws IOException {
        final Path genomeDataPath = genome.getDataPath();
        Files.createDirectories(genomeDataPath);

        LOG.info("Creating test organism");
        final Path fastaPath = Files.createTempFile(genome.getBuild(), ".fa");
        final Path twoBitPath = genomeDataPath.resolve(genome.getBuild() + ".2bit");
        try (final BufferedWriter writer = Files.newBufferedWriter(fastaPath)) {
            for (final String name : CHROMOSOMES.keySet()) {
                final int length = CHROMOSOMES.get(name);
                final char[] randomSeq = Sampling.sampleString(
                        Nucleotide.ALPHABET, length, 0.3, 0.2, 0.3, 0.2).toCharArray();

                // gaps at least 3
                final int gapsCount = RANDOM.nextInt(MAX_GAPS_IN_CHR - 3) + 3;
                for (int gapI = 0; gapI < gapsCount; gapI++) {
                    // gap at least 1 bp length
                    final int gapLength = RANDOM.nextInt(MAX_GAP_LENGTH_BP - 1) + 1;
                    final int gapPos = RANDOM.nextInt(length - gapLength);
                    for (int offset = gapPos; offset < gapPos + gapLength; offset++) {
                        randomSeq[offset] = 'n';
                    }
                }

                writer.write('>' + name + System.lineSeparator());
                writer.write(new String(randomSeq) + System.lineSeparator());
            }
        }

        LOG.info("Converting FASTA to 2bit");
        TwoBitWriter.convert(fastaPath, twoBitPath);

        Files.delete(fastaPath);
    }

    private static void createAnnotations(final Genome genome) throws IOException {
        final Path genesPath = genome.getDataPath().resolve("genes.json");

        final Gson gson = Genes.GSON;
        try (final BufferedWriter w = Files.newBufferedWriter(genesPath);
             final JsonWriter json = new JsonWriter(w)) {
            json.beginArray();

            final List<String> names = Lists.newArrayList(CHROMOSOMES.keySet());
            names.sort(Ordering.usingToString());
            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);
                final int length = CHROMOSOMES.get(name);

                int currentEnd = 0;
                int geneNumber = 0;
                while (length - currentEnd > 6e3) {
                    final String geneName = ("simgene." + name + '.' + geneNumber).toUpperCase();
                    final int currentStart = currentEnd + (int) 1e3 + RANDOM.nextInt((int) 4e3);
                    final int currentLength
                            = (int) 1e3 + Math.min(length - currentStart - (int) 1e3, (int) 1e4);
                    final Strand strand = RANDOM.nextBoolean() ? Strand.PLUS : Strand.MINUS;
                    final Gene gene
                            = createGeneAnnotation(geneName, Chromosome.get(genome, i),
                            strand, currentStart, currentLength);
                    gson.toJson(gson.toJsonTree(gene), json);

                    currentEnd = currentStart + currentLength;
                    geneNumber++;
                }
            }
            json.endArray();
        }
    }

    /**
     * Creates a random gene annotation given the name, the start position, the length, the chromosome and the strand.
     *
     * @param length The length. Should be greater than 33 (accommodating 8 exons and 9 introns, 2 bp each).
     */
    private static Gene createGeneAnnotation(final String geneName,
                                             final Chromosome chromosome, final Strand strand,
                                             final int start, final int length) {
        if (length <= 33) {
            throw new IllegalArgumentException("createGene(): length too short.");
        }

        final int exonCount = 1 + RANDOM.nextInt(8);
        final boolean leftFlankingIntron = RANDOM.nextBoolean();
        final boolean rightFlankingIntron = RANDOM.nextBoolean();
        final List<Integer> exonStarts = new ArrayList<>(exonCount);
        final List<Integer> exonEnds = new ArrayList<>(exonCount);
        int lengthPool = length - 4 * exonCount + 2 - (leftFlankingIntron ? 2 : 0) - (rightFlankingIntron ? 2 : 0);
        exonStarts.add(0, leftFlankingIntron ? start + 2 + RANDOM.nextInt(lengthPool) : start);
        if (leftFlankingIntron) {
            lengthPool -= exonStarts.get(0) - start - 2;
        }
        for (int i = 0; i < exonCount - 1; ++i) {
            exonEnds.add(i, exonStarts.get(i) + 2 + RANDOM.nextInt(lengthPool));
            lengthPool -= exonEnds.get(i) - exonStarts.get(i) - 2;
            exonStarts.add(i + 1, exonEnds.get(i) + 2 + RANDOM.nextInt(lengthPool));
            lengthPool -= exonStarts.get(i + 1) - exonEnds.get(i) - 2;
        }
        exonEnds.add(exonCount - 1,
                rightFlankingIntron ?
                        exonStarts.get(exonCount - 1) + 2 + RANDOM.nextInt(lengthPool) :
                        start + length);
        final int mRNALength = exonEnds.stream().reduce((x, y) -> x + y).get()
                - exonStarts.stream().reduce((x, y) -> x + y).get();
        final int cdsLength = (RANDOM.nextInt(5) == 0 || mRNALength < 3) ? 0 : RANDOM.nextInt(mRNALength / 3) * 3;
        int cdsStartOffset = RANDOM.nextInt(mRNALength - cdsLength);
        int cdsStartExon = 0;
        while (cdsStartOffset >= exonEnds.get(cdsStartExon) - exonStarts.get(cdsStartExon)) {
            cdsStartOffset -= exonEnds.get(cdsStartExon) - exonStarts.get(cdsStartExon);
            ++cdsStartExon;
        }

        final int cdsStart = exonStarts.get(cdsStartExon) + cdsStartOffset;

        while (cdsStartOffset + cdsLength > exonEnds.get(cdsStartExon) - exonStarts.get(cdsStartExon)) {
            cdsStartOffset -= exonEnds.get(cdsStartExon) - exonStarts.get(cdsStartExon);
            ++cdsStartExon;
        }

        final int cdsEnd = exonStarts.get(cdsStartExon) + cdsStartOffset + cdsLength;

        final ArrayList<Range> exonRanges = new ArrayList<>();
        for (int i = 0; i < exonCount; i++) {
            exonRanges.add(new Range(exonStarts.get(i), exonEnds.get(i)));
        }


        return new Gene(geneName, geneName, geneName, geneName,
                new Location(start, start + length, chromosome, strand),
                new Range(cdsStart, cdsEnd),
                exonRanges);
    }

    public static void createGaps(final Genome genome) {
        final Path genesPath = genome.getDataPath().resolve(Gaps.GAPS_FILE_NAME);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(genesPath.toFile()))))) {
            final CSVPrinter printer = Gaps.FORMAT.print(w);
            for (Chromosome chromosome : genome.getChromosomes()) {
                printer.printRecord(100,
                        chromosome.getName(),
                        chromosome.getLength() / 2 - 1000, chromosome.getLength() / 2 + 1000,
                        "", "", 2000, "centromere", "");
            }
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    public static void createCytoBands(final Genome genome) {
        final Path genesPath = genome.getDataPath().resolve(CytoBands.CYTOBANDS_FILE_NAME);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(genesPath.toFile()))))) {
            final CSVPrinter printer = CytoBands.FORMAT.print(w);
            for (Chromosome chromosome : genome.getChromosomes()) {
                printer.printRecord(chromosome.getName(),
                        chromosome.getLength() / 2 - 1000, chromosome.getLength() / 2 + 1000,
                        "centromere" + chromosome.getName(),
                        "unknown region tag");
            }
        } catch (IOException e) {
            LOG.error(e);
        }
    }
}
