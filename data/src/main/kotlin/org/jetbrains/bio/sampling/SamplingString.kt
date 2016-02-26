package org.jetbrains.bio.sampling

import org.apache.commons.math3.random.RandomDataGenerator

/**
 * Sampling procedures for common statistical distributions.
 *
 * @author Alexey Dievsky
 * @author Sergei Lebedev
 * @author Oleg Shpynov
 * @since 18/03/13
 */
object Sampling {
    @JvmField val RANDOM_DATA_GENERATOR: RandomDataGenerator = RandomDataGenerator()

    /**
     * Defaults `probabilities` to be a uniform distribution
     * over characters.
     */
    fun sampleString(alphabet: CharArray, length: Int): String {
        val probabilities = DoubleArray(alphabet.size)
        probabilities.fill(1.0 / alphabet.size)
        return sampleString(alphabet, length, *probabilities)
    }

    /**
     * Samples a random string in a given alphabet.
     *
     * @param alphabet letters, e.g. `"ATCG"`.
     * @param length desired length.
     * @param probabilities probabilities of all (or all but last) letters.
     * @return random string.
     */
    @JvmStatic fun sampleString(alphabet: CharArray, length: Int,
                                vararg probabilities: Double): String {
        val n = probabilities.size
        val d = when {
            alphabet.size == n -> CategoricalDistribution(probabilities)
            alphabet.size == n - 1 -> {
                val copy = probabilities.copyOf(n - 1)
                copy[n - 1] = 1 - probabilities.sum()
                CategoricalDistribution(copy)
            }
            else -> throw IllegalArgumentException("missing or redundant probabilities")
        }

        val sb = StringBuilder(length)
        for (i in 0..length - 1) {
            sb.append(alphabet[d.sample()])
        }

        return sb.toString()
    }
}

