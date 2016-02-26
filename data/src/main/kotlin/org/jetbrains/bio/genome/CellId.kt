package org.jetbrains.bio.genome

import org.apache.log4j.Logger

data class CellId(val name: String, val description: String) {

    init {
        val key = name.toLowerCase()
        check(key !in CACHE) { "Cell already registered: ${CACHE[key]}}"}
        CACHE[key] = this
    }

    override fun toString() = name

    companion object {
        val LOG = Logger.getLogger(CellId::class.java)
        private val CACHE = hashMapOf<String, CellId>()

        init {
            // Touch classes to Load them in JVM & perform auto-registration
            HumanCells.toString()
            DatasetCells.toString()
        }

        operator @JvmStatic fun get(name: String): CellId {
            if (name.toLowerCase() in CACHE) {
                return CACHE[name.toLowerCase()]!!
            }
            return CellId(name, "N/A")
        }
    }
}

object HumanCells {
    @JvmField val H1: CellId = CellId("h1", "Human H1 male embrionic stem cells")
    @JvmField val H9: CellId = CellId("h9", "Human H9 male embrionic stem cells")
    @JvmField val IMR90: CellId = CellId("imr90", "Human female lung fibroblasts cells")
    @JvmField val MSC: CellId = CellId("msc", "Human mesenchymal stem cells")
    val STEM_CELLS: CellId = CellId("hESC", "Human embrionic stem cells")
    val STEM_FIBROBLAST_CELLS: CellId = CellId("hESC-Fibro", "Fibroblastic differentiated derivative of hESC")
    val NEONATAL_FIBROBLASTS: CellId = CellId("Fibro", "Neonatal fibroblasts")
    val HEP_G2: CellId = CellId("HepG2", "Human Hepatoma Cells")
    val HMEC: CellId = CellId("hMEC", "Human Mammary Epithelial Cells")
    val HUVEC: CellId = CellId("HUVEC", "Human umbilical vein endothelial cell")
    val GM12878: CellId = CellId("GM12878", "Human lymphoblastoid cell line")
    val K562: CellId = CellId("K562", "Human immortalised myelogenous leukemia line")
    val HSMM: CellId = CellId("HSMM", "Human Skeletal muscle myotubes")
    val NHLF: CellId = CellId("NHLF", "Human lung fibroblasts")
    val NHEK: CellId = CellId("NHEK", "Human epidermal keratinocytes")
    @JvmField val AL: CellId = CellId("AL", "Human atherosclerotic lesion")
    @JvmField val AO: CellId = CellId("AO", "Human aortic tissue")
}

object DatasetCells {
    val HESC1_SIM: CellId = CellId("hesc1-sim", "Simulated human embryo cell, sample 1")
    val HESC2_SIM: CellId = CellId("hesc2-sim", "Simulated human embryo cell, sample 2")
    val HDC1_SIM: CellId = CellId("hdc1-sim", "Simulated human differentiated cell, sample 1")
}
