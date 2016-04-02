package org.jetbrains.bio.genome;

import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.genome.query.GenomeQuery;
import org.jetbrains.bio.genome.query.locus.*;
import org.jetbrains.bio.util.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author Oleg Shpynov
 * @since 11/11/13
 */
public class ImportantGenesAndLoci {
  private static final Logger LOG = Logger.getLogger(ImportantGenesAndLoci.class);

  /**
   * Summarized regulatory loci collected from the following articles
   * <p>
   * [0] Definition in wikipedia
   * https://en.wikipedia.org/wiki/Promoter_(genetics)#cite_note-Smale2003-3
   * <p>
   * [1] Transcriptional Regulatory Elements in the Human Genome, 2006
   * http://www.annualreviews.org.sci-hub.org/doi/abs/10.1146/annurev.genom.7.080505.115623
   * NO markup. Notions of promoter, core promoter, proximal promoter, distal promoter, enhancer, silencer.
   * <p>
   * [2] Identification and analysis of functional elements in 1% of the human genome by the ENCODE pilot project, 2007
   * http://www.nature.com.sci-hub.org/nature/journal/v447/n7146/full/nature05874.html
   * No markup.
   * <p>
   * [3] Mapping and analysis of chromatin state dynamics in nine human cell types, 2011
   * http://www.ncbi.nlm.nih.gov/pubmed/21441907/
   * ChromHMM provides markup CGI, Lamina, Exon, Gene, TES, TSS, TSS2kb
   * <p>
   * [4] Comparative Epigenomic Annotation of Regulatory DNA, 2012
   * http://www.cell.com/abstract/S0092-8674%2812%2900574-0
   * REGULATORY:
   * Up500, 5-UTR, Exon, Coding exon, Trans Body, Intron, 3-UTR, Down 500, Intergenic
   * Promoters:  orthologous promoters (4,000 bp centered at TSS)
   * <p>
   * [5] An integrated encyclopedia of DNA elements in the human genome, 2012
   * http://www.nature.com.sci-hub.org/nature/journal/v489/n7414/full/nature11247.html
   * No markup, promoters: tss ±400bp
   * <p>
   * [6] Transcriptional and Epigenetic Dynamics during Specification of Human Embryonic Stem Cells, 2013
   * http://www.sciencedirect.com/science/article/pii/S0092867413005138
   * <p>
   * [7] Epigenomic Analysis of Multilineage Differentiation of Human Embryonic Stem Cells, 2013
   * http://www.sciencedirect.com/science/article/pii/S0092867413004649
   * Promoters: Histone modifications, TSS ± 2 kb; DNA methylation, TSS ± 200 bp; promoter CG density, TSS ± 500 bp
   * Enhancer prediction: ±5kbp. Tool: RFECS (Random Forest for Enhancer Identification using Chromatin States),
   * (Rajagopal et al., 2013)
   * <p>
   * [8] Enhancer-core-promoter specificity separates developmental and housekeeping gene regulation, 2014
   * http://sci-hub.bz/8eb094d76a9c587144981526a6ad45fd/zabidi2014.pdf
   * Different strategies possible
   * We performed three different strategies of enhancer to gene assignments:
   * (1) ‘closest TSS’, whereby an enhancer is assigned to the closest TSS of an annotated transcript;
   * (2) ‘1 kb TSS’, whereby an enhancer is assigned to all TSSs that are within 1 kb; and
   * (3) ‘gene loci’, whereby an enhancer is assigned to a gene provided that it falls within 5 kb upstream from the TSS
   * , within the gene body itself, or 2 kb downstream of the gene (multiple assigned genes are possible)
   */

  public static final List<LocusQuery<Gene>> REGULATORY = ImmutableList.of(
      new TssQuery(-5000, -2500),       // [1] Distal enhancer
      new TssQuery(),                   // [3, 4, 7] Default ±2kbp - average
      new UTR5Query(),                  // [4]
      new UTR3Query(),                  // [4]
      new CDSQuery(),                   // [4]
      new IntronsQuery(),               // [4]
      new ExonsQuery(),                 // [4]
      new TranscriptQuery(),            // Important for H3K36me3
      new TesQuery(),                   // Default ±2kbp
      new TesQuery(2500, 5000));        // Distal TES

  private static final String[] DEVELOPMENTAL_GENES = new String[]{
      // Pluripotency factors
      // https://biolabs.intellij.net/index.php?title=File:Jc_imb2013_tfcp2l1_clustering.png
      // oct4
      "POU5F1", // uc003nsu.1(NM_203289, Q5STF4);
      // oct3
      "POU5F1", // uc003nsv.1(NM_002701, Q01860);
      // p5f1b
      "POU5F1", // uc003ysf.1(NR_002304, Q2VIK6);

      "SOX1", // uc001vsb.1(NM_005986, Q5W0Q1);
      "SOX2", // uc003fkx.1(NM_003106, P48431);
      "SOX3", // uc004fbd.1(NM_005634, P41225);
      "SOX15", // uc002ghz.1(NM_006942, O60248); uc002ghy.1(NM_006942, O60248);
      "SOX18", // uc002yhs.1(NM_018419, P35713);

      "NANOG", // uc009zfy.1(NM_024865, Q9H9S0);

      "GLIS1", // uc001cvr.1(NM_147193, Q2M3G9);

      "KLF1", // uc002mvo.1(NM_006563, Q13351);
      "KLF4", // uc004bdg.1(NM_004235, O43474); uc004bdh.1(NM_004235, Q8N717); uc004bdf.1(NM_004235, O43474-2);
      "KLF5", // uc001vje.1(NM_001730, Q13887); uc001vjd.1(NM_001730, A2TJX0);

      "TCF3", // uc002ltp.1(NM_003200, P15923); uc002ltr.1(NM_003200, P15923); uc002ltt.2(Q2TB40); uc002ltq.1(NM_003200, Q2TB39); uc002lts.1(Q2TB40); uc002lto.1(NM_003200, ); uc002ltn.1();
      "STAT3", // uc002hzk.1(NM_213662, ); uc002hzl.1(NM_139276, P40763); uc002hzm.1(NM_003150, P40763-2); uc002hzn.1(NM_139276, P40763);
      "TFCP2L1", // uc002tmx.1(NM_014553, Q9NZI6); uc010flr.1(NM_014553, Q5JV87);

      // Oct4 integrated interactome
      // http://www.sciencedirect.com/science/article/pii/S1934590910000913
      // Nurd complex
      "CHD4", // uc001qpo.1(NM_001273, Q14839); uc001qpp.1(NM_001273, Q14839-2); uc001qpn.1(NM_001273, Q14839);
      "MTA1", // uc001yqx.1(NM_004689, A5PLK4); uc001yqy.1(); uc001yrb.1(NM_004689, Q59FW1); uc001yqz.1(NM_004689, Q86TR6); uc001yra.1(NM_004689, Q86TR6);
      "GATAD2A", // uc002nmp.2(NM_017660, Q86YP4); uc002nmm.2(NM_017660, Q86YP4);
      "MTA2", // uc001ntq.1(NM_004739, O94776);
      "GATAD2B", // uc001fdb.2(NM_020699, Q8WXI9);
      "HDAC1", // uc001bvb.1(NM_004964, Q13547); uc001bvc.1(NM_004964, Q5HYD4);
      "MBD3", // uc002ltl.1(NM_003926, O95983); uc002ltk.2(NM_003926, O95983-2); uc002ltj.2(NM_003926, O95983);
      "MTA3", // uc002rsq.1(NM_020744, Q9BTC8-2); uc002rsr.1(NM_020744, Q9BTC8-2); uc002rso.1(NM_020744, Q9BTC8); uc002rsp.1(NM_020744, Q9BTC8);
      "HDAC2", // uc003pwd.1(NM_001527, Q92769); uc003pwe.1(); uc003pwc.1(NM_001527, Q92769);
      "RBBP7", // uc004cxt.1(NM_002893, Q16576); uc004cxs.1(NM_002893, Q5JP00); uc004cxu.1(NM_002893, B0R0W4); uc010nez.1();

      // SWI/SNF complex: Baf155 = (smarcc1), brg1 = (smarca4)
      "SMARCC1", // uc003crq.2(NM_003074, Q58EY4);
      "SMARCA4", // uc010dxo.1(NM_001128849, Q9HBD4); uc002mqf.2(NM_003072, P51532); uc010dxp.1(NM_001128844, P51532); uc010dxq.1(NM_001128847, B1A8Z7); uc010dxr.1(NM_001128848, B1A8Z4); uc002mqj.2(NM_001128845, B1A8Z5); uc010dxs.1(NM_001128846, B1A8Z6); uc002mqg.1(NM_001128848, Q59FZ6); uc002mqh.2(NM_001128847, ); uc010dxt.1(NM_001128848, ); uc002mqe.2(NM_001128848, A5D6X0); uc002mqi.1(NM_001128848, );

      // PRC1 complex
      // Ring1b - ? (Ring1 = Q06587)
      "PHC1", // uc001qvd.1(NM_004426, B2RXH1); uc001qve.1(NM_004426, B2RXH1); uc001qvc.1();
      "RING1", // uc003odk.1(NM_002931, Q06587);
      "RYBP", // uc003dpe.1(NM_012234, Q8N488);

      // Trrap/p400 complex
      //TODO Trapp - huge complex  (Bet5, Bet3, Trs20, Trs23, Trs31, Trs33, Trs85) trapp II,= (Trs65, Trs120 and Trs130)
      //    Bet5 = (trappc1/Q9Y5R8),
      //    Bet3 = (trappc3/O43617),
      //    Trs20 = (trappc2/NP_001011658),
      //    Trs23 = (trappc4/
      //    (TODO)
      "EP400", // uc001ujl.1(NM_015409, A8MSW4); uc001ujn.1(NM_015409, A6NJX1); uc001ujm.1(NM_015409, A7E2D7); uc001ujk.2(NM_015409, );

      // LSD1 complex
      // lsd1 = (aof2/O60341) or KDM1A
      "NM_015013", // uc001bgi.1(NM_015013, O60341);
      "ZMYM2", // uc001umr.1(NM_003453, Q9UBW7); uc001ums.1(NM_197968, Q9UBW7); uc001umt.1(NM_197968, Q9UBW7); uc001umv.1(Q5W0Q3); uc001umw.1(NM_197968, Q5W0Q3); uc001umq.2(NM_197968, Q5W0T3);
      "RCOR2", // uc001nyc.1(NM_173587, Q8IZ40);

      // Transcription factors
      //    tcfcp2l1 =? = tcfcp2 /mus musculus/ ~ tfcp2 /human/,
      //    Requiem = (dpf2/Q927850),
      //    Dax1 = (nr0b1/P51843),
      //    zfp143 = (znf143/P52747)
      //    0610010K14Rik /mus musculus/ ~ (C12orf49/Q9H741) /human/
      //    2810474O19Rik /mus musculus/ ~ (C12orf35/Q9HCM1) /human/
      "SALL4", // uc002xwh.2(NM_020436, Q9UJQ4); uc010gii.1(NM_020436, A2A2D8); uc002xwi.2(Q6Y8G5);
      "SALL1", // uc010cbt.1(NM_001127892, ); uc010cbu.1(NM_002968, ); uc002egt.2(NM_001127892, Q9NSC2); uc010cbv.1(Q14CE9);
      "ZNF219", // uc001vzs.2(NM_001101672, Q9P2Y4); uc010aik.1(NM_001102454, Q9P2Y4); uc001vzr.2(NM_016423, Q9P2Y4);
      "ARID3B", // uc002ayd.1(NM_006465, Q8IVW6); uc002aye.1(NM_006465, Q8IVW6); uc002ayc.2(NM_006465, Q8IVW6-2); uc010bjs.1(NM_006465, );
      "WDR5", // uc004cey.1(NM_017588, P61964); uc004cez.1(NM_052821, P61964);
      "ZNF462", // uc004bcz.1(NM_021224, Q68CP0); uc010mto.1(NM_021224, Q96JM2); uc004bda.1(NM_021224, Q5T0T4); uc004bdb.1(Q5T0T2);
      "MGA", // uc001zoh.1(NM_001080541, Q8IWI9); uc001zog.1(NM_001080541, ); uc001zoi.2();
      "UBP1", // uc010hga.1(NM_001128161, Q9NZI7); uc003cfq.2(NM_014517, Q9NZI7); uc003cfr.2(NM_001128160, Q9NZI7-4);
      "NACC1", // uc002mwm.1(NM_052876, Q96RE7);
      "HCFC1", // uc004fjp.1(NM_005334, P51610);
      "HELLS", // uc001kjt.1(NM_018063, Q9NRZ9); uc009xuo.1(NM_018063, B2RB41); uc001kju.1(NM_018063, Q9NRZ9-3); uc001kjs.1(NM_018063, Q9NRZ9-2); uc009xul.1(NM_018063, Q9NRZ9-5); uc009xum.1(NM_018063, Q9NRZ9-6);
      "RBPJ", // uc003gsa.1(NM_203284, Q06330-2); uc003grx.1(NM_005349, Q06330-2); uc003gry.1(NM_203283, Q06330-2); uc003gsb.1(NM_015874, Q06330-2); uc003grz.1(NM_005349, Q06330-2); uc003gsc.1();
      "TFCP2", // uc001rxw.1(NM_005653, Q12800); uc009zly.1(NM_005653, A8K5E9); uc001rxv.1(NM_005653, A8K5E9); uc009zlx.1(NM_005653, Q12800-2); uc001rxx.1(NM_005653, Q12800);
      "DPF2", // uc001odm.1(NM_006268, Q92785); uc001odn.1(NM_006268, A8K7C9);
      "ESRRB", // uc001xso.1(NM_004452, O95718); uc001xsr.1(Q5F0P7); uc001xsq.1(NM_004452, Q5F0P8);
      "PML", // uc002awk.1(NM_033239, Q9BZX7); uc002awm.1(NM_033240, P29590-2); uc002awv.1(NM_033238, P29590); uc002awn.1(NM_033244, P29590-4); uc002awr.1(NM_002675, P29590-5); uc002awo.1(NM_033250, Q9BPW2); uc002aws.1(NM_033249, Q9BZX6); uc002awp.1(NM_033247, Q59GQ8); uc002awt.1(NM_033246, Q9BPW2); uc002awq.1(NM_033244, P29590-4); uc002awu.1(NM_033246, Q59FP9); uc002awl.1(NM_033246, Q9BPW2); uc002awj.1(Q59H09); uc002aww.1(NM_033246, Q59GQ8); uc002awx.1(NM_033246, ); uc002awy.1(Q71RA7);
      "FOXP4", // uc003oql.1(NM_001012426, Q8IVH2); uc003oqm.1(NM_001012427, Q7Z7F8); uc003oqn.1(NM_138457, Q8IW55);
      "CTBP2", // uc009yal.1(NM_001329, P56545); uc001lih.2(NM_001083914, P56545); uc001lie.2(NM_022802, P56545-2); uc001lif.2(NM_001329, P56545); uc009yak.1(NM_001329, P56545); uc001lid.2(NM_001329, Q5SQP8);
      "NR0B1", // uc004dcf.2(NM_000475, P51843);
      "ZNF143", // uc001mhr.1(NM_003442, P52747); uc009yfu.1(NM_003442, P52747);
      "KLF5", // uc001vje.1(NM_001730, Q13887); uc001vjd.1(NM_001730, A2TJX0);

      //, sox2 (already mentioned)

      // Other
      "RIF1", // uc002txm.1(NM_018151, Q5UIP0); uc002txn.1(NM_018151, Q5UIP0-2); uc002txl.1(NM_018151, Q5UIP0-2); uc002txo.1(NM_018151, Q5UIP0-2); uc010fnv.1(NM_018151, ); uc002txp.2(NM_018151, );
      "L1TD1", // uc001dae.2(NM_019079, Q5T7N2);
      "AKAP8", // uc002nav.1(NM_005858, O43823);
      "MSH2", // uc002rvy.1(NM_000251, P43246); uc002rvz.2(NM_000251, Q53RU4); uc010fbi.1(); uc010fbf.1(Q0ZAJ2); uc010fbg.1(Q0ZAJ1); uc010fbh.1();
      "OGT", // uc004eaa.1(NM_181672, O15294); uc004eab.1(NM_181673, O15294-3); uc004eac.2(NM_181673, Q548W1);
      "RBM14", // uc001oit.1(NM_006328, Q96PK6); uc009yri.1(Q96PK6-2); uc009yrh.1(Q2PYN1); uc001oiu.2();
      "FRG1", // uc003izs.1(NM_004477, Q14331);
      "SMC1A", // uc004dsg.1(NM_006306, Q14683); uc004dsh.1(NM_006306, Q6MZR8); uc004dsi.1();
      "C11ORF30", // uc001oxl.1(NM_020193, Q7Z589); uc001oxm.1(NM_020193, Q17RM7); uc001oxn.1(NM_020193, Q17RM7); uc009yuj.1(NM_020193, ); uc001oxo.1(NM_020193, );
      "C12ORF49", // uc001tvz.1(NM_024738, Q9H741); uc009zwm.1();
      // Q9HCM1 ~ C11ORF30 gene, not available in hg19,gh38(KIAA1551)
      "Q9HCM1", // uc001rks.1(NM_018169, Q9HCM1);
      "ZCCHC8", // uc001ucn.1(NM_017612, Q6NZY4); uc009zxp.1(Q6NZY4-2); uc009zxq.1(Q6NZY4-2); uc001ucm.1(NM_017612, Q6NZY4-2); uc001ucl.1(NM_017612, );
  };

  /**
   * Short list provided by :
   * "Human housekeeping genes revisited" E. Eisenberg and E.Y. Levanon, Trends in Genetics, 29 (2013)
   * <p>
   * REM: short list: only 4 of 11 genes is mentioned in 2003 (NM_014453, NM_000175, NM_002794, NM_002796)
   * REM: full list: only 285 of 566 genes is mentioned in 2003
   * REM: full list: only 5 of first 2003 list (aka best expression) 10 genes is in full list
   */
  private static final String[] HOUSE_KEEPING_GENES_2013_SHORT = new String[]{
      "NM_015449", // chr1: C1orf43  (chromosome 1 open reading frame 43)
      "NM_014453", // chr19: CHMP2A  (charged multivesicular body protein 2A)
      "NM_020154", // chr15: EMC7    (ER membrane protein complex subunit 7)
      "NM_000175", // chr19: GPI     (glucose-6-phosphate isomerase)
      "NM_002794", // chr1: PSMB2    (proteasome subunit, beta type, 2)
      "NM_002796", // chr1: PSMB4    (proteasome subunit, beta type, 4 )
      "NM_004637", // chr3: RAB7A    (member RAS oncogene family )
      "NM_005669", // chr5: REEP5    (receptor accessory protein 5 )
      "NM_004175", // chr22: SNRPD3  (small nuclear ribonucleoprotein D3)
      "NM_007126", // chr9: VCP      (valosin containing protein)
      "NM_016226", // chr12: VPS29   (vacuolar protein sorting 29 homolog)
  };

  public static String[] getHouseKeepingGenes2013Short(final GenomeQuery genomeQuery) {
    return resolveGeneNames(genomeQuery, HOUSE_KEEPING_GENES_2013_SHORT);
  }

  /**
   * 3804 genes, full list at : http://www.tau.ac.il/~elieis/HKG/HK_genes.txt
   * <p>
   * Alternative list of HK genes
   * Lists of somatic tissue-specific genes and housekeeping genes were obtained from (Zhu et al., 2008)
   * http://www.wikicell.org/index.php/HK_Gene
   */
  public static String[] getHouseKeepingGenes2013Full() {
    final List<String> genesNames = new ArrayList<>();

    final Path rawDataPath = Configuration.INSTANCE.getRawDataPath();
    final Path genesFile = rawDataPath.resolve("housekeeping_genes").resolve("HK_genes_2013.txt");
    try (Stream<String> lines = Files.lines(genesFile)) {
      lines.forEach(line -> {
        final int geneIdStartIndex = line.lastIndexOf('\t');
        genesNames.add(line.substring(geneIdStartIndex + 1));
      });
    } catch (final IOException e) {
      LOG.error("Cannot load house keeping genes list: " + genesFile.toAbsolutePath().toString(), e);
    }
    return genesNames.stream().toArray(String[]::new);
  }

  public static String[] getDevelopmentalGenes(@NotNull final GenomeQuery genomeQuery) {
    checkArgument(genomeQuery.getGenome().getSpecies().equals("hg"),
                  "Development genes are available only for Homo Sapience");

    // remove duplicates + replace with unique gene ids
    // unique sometime depends on genome build, so we need to know build
    return resolveGeneNames(genomeQuery, DEVELOPMENTAL_GENES);
  }

  private static String[] resolveGeneNames(final @NotNull GenomeQuery genomeQuery,
                                           final String[] geneNames) {
    final String build = genomeQuery.getBuild();
    return Arrays.stream(geneNames)
        .flatMap(geneName -> GeneResolver.get(build, geneName).stream())
        .map(Gene::getSymbol).distinct()  // filter unique names
        .toArray(String[]::new);
  }
}

