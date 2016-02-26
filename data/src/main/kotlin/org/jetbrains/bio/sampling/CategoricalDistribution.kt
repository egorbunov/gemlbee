package org.jetbrains.bio.sampling

import com.google.common.primitives.Ints
import gnu.trove.stack.array.TIntArrayStack
import org.apache.commons.math3.distribution.AbstractIntegerDistribution
import org.apache.commons.math3.random.RandomGenerator
import org.jetbrains.bio.viktor.StridedVector
import org.jetbrains.bio.viktor.asStrided

/**
 * An empirical distribution for discrete data.
 *
 * Uses Alias algorithm described in http://www.keithschwarz.com/darts-dice-coins.
 *
 * @author Sergei Lebedev
 * @since 16/09/13
 */
class CategoricalDistribution @JvmOverloads constructor(
        private val probabilities: StridedVector,
        randomGenerator: RandomGenerator = Sampling.RANDOM_DATA_GENERATOR.randomGenerator) :
        AbstractIntegerDistribution(randomGenerator) {

    // Remove this once we get rid of all the Java usages.
    constructor(probabilities: DoubleArray) : this(probabilities.asStrided()) {}

    private val aliasTable: IntArray
    private val aliasProbabilities: DoubleArray

    init {
        val n = probabilities.size()
        require(n > 0) { "no data" }

        // Init alias structures as described in http://www.keithschwarz.com/darts-dice-coins
        aliasProbabilities = DoubleArray(n)
        aliasTable = IntArray(n)
        val small = TIntArrayStack()
        val large = TIntArrayStack()

        val p = probabilities.copy()
        p.rescale()
        val average = 1.0 / n
        for (i in 0..n - 1) {
            if (p[i] < average) {
                small.push(i)
            } else {
                large.push(i)
            }
        }

        while (small.size() > 0 && large.size() > 0) {
            val l = small.pop()
            val g = large.pop()

            aliasProbabilities[l] = p[l] * n
            aliasTable[l] = g

            p[g] = (p[g] + p[l]) - average
            if (p[g] < average) {
                small.push(g)
            } else {
                large.push(g)
            }
        }

        for (g in large.toArray()) {
            aliasProbabilities[g] = 1.0
        }

        for (l in small.toArray()) {
            aliasProbabilities[l] = 1.0
        }
    }

    override fun probability(x: Int): Double {
        if (x < supportLowerBound || x > supportUpperBound) {
            return 0.0
        } else {
            return probabilities[x - 1]
        }
    }

    override fun cumulativeProbability(x: Int): Double {
        return when {
            x < supportLowerBound -> 0.0
            x >= supportUpperBound -> 1.0
            else -> probabilities.slice(0, x + 1).sum()
        }
    }

    // Note(lebedev): mean and variance are defined component-wise, see
    // http://en.wikipedia.org/wiki/Categorical_distribution

    override fun getNumericalMean(): Double {
        throw UnsupportedOperationException()
    }

    override fun getNumericalVariance(): Double {
        throw UnsupportedOperationException()
    }

    override fun getSupportLowerBound(): Int = 1

    override fun getSupportUpperBound(): Int = probabilities.size()

    override fun isSupportConnected(): Boolean = true

    override fun sample(): Int {
        val i = random.nextInt(aliasProbabilities.size)
        val coinToss = random.nextDouble()
        return if (coinToss < aliasProbabilities[i]) i else aliasTable[i]
    }

    companion object {
        @JvmStatic fun of(values: IntArray): CategoricalDistribution {
            require(Ints.min(*values) >= 1) { "categories must be positive integers" }

            val probabilities = StridedVector(Ints.max(*values))
            for (value in values) {
                probabilities[value - 1] = probabilities[value - 1] + 1
            }

            probabilities.rescale()
            return CategoricalDistribution(probabilities)
        }
    }
}
