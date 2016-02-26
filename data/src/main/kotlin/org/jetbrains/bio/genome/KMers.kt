package org.jetbrains.bio.genome

import com.google.common.math.LongMath
import gnu.trove.map.hash.TObjectIntHashMap
import org.jetbrains.bio.ext.stream
import java.util.*
import java.util.stream.LongStream
import java.util.stream.Stream

/**
 * A basic k-mer counter.
 *
 * @author Sergei Lebedev
 * @since 05/03/15
 */
class KMers private constructor(private val k: Int) {
    private val counts = TObjectIntHashMap<String>()

    fun stream(): Stream<String> = counts.keySet().stream()

    /**
     * Returns the count for a given `kmer` or 0 if the `kmer`
     * was never seen.
     */
    operator fun get(kmer: String): Int = counts[kmer]

    /**
     * Updates internal counters with k-mers from a given `item`.
     */
    fun update(item: String, offset: Int, length: Int) {
        require(offset + length <= item.length)
        for (i in offset..offset + length - k + 1 - 1) {
            counts.adjustOrPutValue(item.substring(i, i + k), 1, 1)
        }
    }

    /**
     * Returns the number of uniquely seen k-mers.
     */
    fun size(): Int = counts.size()

    companion object {
        /**
         * A shortcut for generating an array of ATCG k-mers.
         */
        @JvmStatic fun genomic(k: Int): Array<String> = generate(k, "acgt")

        /**
         * Generates an array of k-mers in a given `alphabet`.
         */
        @JvmStatic fun generate(k: Int, alphabet: String): Array<String> {
            return stream(k, alphabet).toArray { arrayOfNulls<String>(it) }
        }

        /**
         * Streams k-mers in a given `alphabet`.
         */
        @JvmStatic fun stream(k: Int, alphabet: String): Stream<String> {
            return LongStream.range(0, number(k, alphabet))
                    .mapToObj { decode(it, k, alphabet) }
        }

        /**
         * Returns total number of k-mers in a given `alphabet`.
         */
        @JvmStatic fun number(k: Int, alphabet: String): Long {
            return LongMath.pow(alphabet.length.toLong(), k)
        }

        /**
         * Encodes a k-mer as a number in little-endian order.
         *
         * Let N be the `alphabet.size()`, then a k-mer can be
         * represented as a base-N number with digits taken from
         * `alphabet`.
         */
        @JvmStatic fun encode(kmer: String, alphabet: String): Long {
            var acc: Long = 0
            var radix: Long = 1
            for (i in kmer.length - 1 downTo 0) {
                acc += radix * alphabet.indexOf(kmer[i])
                radix *= alphabet.length.toLong()
            }

            return acc
        }

        /**
         * Decodes a k-mer from a number in little-endian order.
         *
         * @see .encode for the encoding idea.
         */
        @JvmStatic fun decode(idx: Long, k: Int, alphabet: String): String {
            val chunk = CharArray(k)
            var acc = idx
            for (i in k - 1 downTo 0) {
                chunk[i] = alphabet[(acc % alphabet.length).toInt()]
                acc /= alphabet.length.toLong()
                if (acc == 0L) {
                    Arrays.fill(chunk, 0, i, alphabet.first())
                    break
                }
            }

            return String(chunk)
        }

        @JvmStatic fun of(k: Int, item: String): KMers {
            return of(k, listOf(item))
        }

        @JvmStatic fun of(k: Int, item: String, offset: Int, length: Int): KMers {
            val kmers = KMers(k)
            kmers.update(item, offset, length)
            return kmers
        }

        @JvmStatic fun of(k: Int, items: Collection<String>): KMers {
            val kmers = KMers(k)
            for (item in items) {
                kmers.update(item, 0, item.length)
            }

            return kmers
        }
    }
}
