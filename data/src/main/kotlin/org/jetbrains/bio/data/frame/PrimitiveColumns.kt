package org.jetbrains.bio.data.frame

import com.google.common.base.MoreObjects
import com.google.common.primitives.*
import gnu.trove.list.array.*
import gnu.trove.set.hash.*
import org.apache.log4j.Logger
import org.jetbrains.bio.data.BitterSet
import java.util.*

// XXX this is done because kotlin.Byte (and other primitives)
// resolve to a _boxed_ java.lang.Byte, instead of a primitive
// class.
@Suppress("platform_class_mapped_to_kotlin") class ByteColumn(label: String, data: ByteArray) :
        Column<ByteArray>(label, data, Byte::class.java) {

    override fun getAsDouble(row: Int) = data[row].toDouble()

    override fun rename(newLabel: String) = ByteColumn(newLabel, data)

    override fun wrap(newData: ByteArray) = ByteColumn(label, newData)

    override fun plus(other: Column<*>): Column<ByteArray> {
        return wrap(Bytes.concat(data, other.data as ByteArray))
    }

    override fun merge(other: Column<*>): Column<ByteArray> {
        val seen = TByteHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TByteArrayList(data)
        for (value in other.data as ByteArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<ByteArray> {
        val copy = ByteArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    override fun intersect(c: Column<*>): ObjIntPredicate<ByteArray> {
        val other = c as ByteColumn
        val seen = TByteHashSet(data)
        seen.retainAll(other.data.clone())
        return ObjIntPredicate { data, i -> data[i] in seen }
    }

    override fun sorted(order: SortOrder): IntArray = data.sortedOrder(order)

    override fun reorder(indices: IntArray): Column<ByteArray> {
        val length = indices.size
        require(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int) = wrap(data.copyOf(newSize))

    override fun size() = data.size

    override fun load(row: Int, value: String) {
        data[row] = java.lang.Byte.parseByte(value)
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is ByteColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class ShortColumn(label: String, data: ShortArray) :
        Column<ShortArray>(label, data, Short::class.java) {

    override fun getAsDouble(row: Int) = data[row].toDouble()

    override fun rename(newLabel: String) = ShortColumn(newLabel, data)

    override fun wrap(newData: ShortArray) = ShortColumn(label, newData)

    override fun plus(other: Column<*>): Column<ShortArray> {
        return wrap(Shorts.concat(data, other.data as ShortArray))
    }

    override fun merge(other: Column<*>): Column<ShortArray> {
        val seen = TShortHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TShortArrayList(data)
        for (value in other.data as ShortArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<ShortArray> {
        val copy = ShortArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    override fun sorted(order: SortOrder) = data.sortedOrder(order)

    override fun reorder(indices: IntArray): Column<ShortArray> {
        val length = indices.size
        require(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int): Column<ShortArray> = wrap(data.copyOf(newSize))

    override fun intersect(c: Column<*>): ObjIntPredicate<ShortArray> {
        val other = c as ShortColumn
        val seen = TShortHashSet(data)
        seen.retainAll(other.data)
        return ObjIntPredicate { data, i -> data[i] in seen }
    }

    override fun size(): Int = data.size

    override fun load(row: Int, value: String) {
        data[row] = value.toShort()
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is ShortColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class IntColumn(label: String, data: IntArray) :
        Column<IntArray>(label, data, Integer::class.java) {

    override fun getAsDouble(row: Int) = data[row].toDouble()

    override fun rename(newLabel: String) = IntColumn(newLabel, data)

    override fun wrap(newData: IntArray) = IntColumn(label, newData)

    override fun plus(other: Column<*>): Column<IntArray> {
        return wrap(Ints.concat(data, other.data as IntArray))
    }

    override fun merge(other: Column<*>): Column<IntArray> {
        val seen = TIntHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TIntArrayList(data)
        for (value in other.data as IntArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<IntArray> {
        val copy = IntArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    override fun sorted(order: SortOrder) = data.sortedOrder(order)

    override fun reorder(indices: IntArray): Column<IntArray> {
        val length = indices.size
        check(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int): Column<IntArray> {
        if (newSize < 0) {
            Logger.getRootLogger().error("Current size: ${size()}, new size = $newSize")
        }
        return wrap(Arrays.copyOf(data, newSize))
    }

    override fun intersect(c: Column<*>): ObjIntPredicate<IntArray> {
        val other = c as IntColumn
        val seen = TIntHashSet(data)
        seen.retainAll(other.data.clone())
        return ObjIntPredicate { data, i -> data[i] in seen }
    }

    override fun size() = data.size

    override fun load(row: Int, value: String) {
        data[row] = value.toInt()
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is IntColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class LongColumn(label: String, data: LongArray) :
        Column<LongArray>(label, data, Long::class.java) {

    override fun getAsDouble(row: Int) = data[row].toDouble()

    override fun rename(newLabel: String) = LongColumn(newLabel, data)

    override fun wrap(newData: LongArray) = LongColumn(label, newData)

    override fun plus(other: Column<*>): Column<LongArray> {
        return wrap(Longs.concat(data, other.data as LongArray))
    }

    override fun merge(other: Column<*>): Column<LongArray> {
        val seen = TLongHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TLongArrayList(data)
        for (value in other.data as LongArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<LongArray> {
        val copy = LongArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    @Deprecated("")
    override fun intersect(c: Column<*>): ObjIntPredicate<LongArray> {
        // BitSet doesn't allow long indices.
        throw UnsupportedOperationException()
    }

    override fun sorted(order: SortOrder) = data.sortedOrder(order)

    override fun reorder(indices: IntArray): Column<LongArray> {
        val length = indices.size
        require(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int): Column<LongArray> = wrap(data.copyOf(newSize))

    override fun size() = data.size

    override fun load(row: Int, value: String) {
        data[row] = value.toLong()
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LongColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class DoubleColumn(label: String, data: DoubleArray) :
        Column<DoubleArray>(label, data, Double::class.java) {

    override fun getAsDouble(row: Int) = data[row]

    override fun rename(newLabel: String) = DoubleColumn(newLabel, data)

    override fun wrap(newData: DoubleArray) = DoubleColumn(label, newData)

    override fun plus(other: Column<*>): Column<DoubleArray> {
        return wrap(Doubles.concat(data, other.data as DoubleArray))
    }

    override fun merge(other: Column<*>): Column<DoubleArray> {
        val seen = TDoubleHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TDoubleArrayList(data)
        for (value in other.data as DoubleArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<DoubleArray> {
        val copy = DoubleArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    override fun sorted(order: SortOrder) = data.argSort(order)

    override fun reorder(indices: IntArray): Column<DoubleArray> {
        val length = indices.size
        require(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int) = wrap(Arrays.copyOf(data, newSize))

    override fun intersect(c: Column<*>): ObjIntPredicate<DoubleArray> {
        val other = c as DoubleColumn
        val seen = TDoubleHashSet(data)
        seen.retainAll(other.data.clone())
        return ObjIntPredicate { data, i -> data[i] in seen }
    }

    override fun size(): Int = data.size

    override fun load(row: Int, value: String) {
        data[row] = value.toDouble()
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DoubleColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class FloatColumn(label: String, data: FloatArray) :
        Column<FloatArray>(label, data, Float::class.java) {

    override fun getAsDouble(row: Int) = data[row].toDouble()

    override fun rename(newLabel: String) = FloatColumn(newLabel, data)

    override fun wrap(newData: FloatArray) = FloatColumn(label, newData)

    override fun plus(other: Column<*>): Column<FloatArray> {
        return wrap(Floats.concat(data, other.data as FloatArray))
    }

    override fun merge(other: Column<*>): Column<FloatArray> {
        val seen = TFloatHashSet(data)
        check(seen.size() == data.size) { "duplicates found" }

        val merged = TFloatArrayList(data)
        for (value in other.data as FloatArray) {
            if (value !in seen) {
                merged.add(value)
                seen.add(value)
            }
        }

        return wrap(merged.toArray())
    }

    override fun filter(mask: BitSet): Column<FloatArray> {
        val copy = FloatArray(mask.cardinality())
        var offset = 0
        for (i in data.indices) {
            if (mask[i]) {
                copy[offset++] = data[i]
            }
        }

        return wrap(copy)
    }

    override fun sorted(order: SortOrder) = data.sortedOrder(order)

    override fun reorder(indices: IntArray): Column<FloatArray> {
        val length = indices.size
        require(data.size == length)

        val clone = data.clone()
        for (i in 0..length - 1) {
            clone[i] = data[indices[i]]
        }

        return wrap(clone)
    }

    override fun resize(newSize: Int): Column<FloatArray> = wrap(Arrays.copyOf(data, newSize))

    override fun intersect(c: Column<*>): ObjIntPredicate<FloatArray> {
        val other = c as FloatColumn
        val seen = TFloatHashSet(data)
        seen.retainAll(other.data.clone())
        return ObjIntPredicate { data, i -> data[i] in seen }
    }

    override fun size() = data.size

    override fun load(row: Int, value: String) {
        data[row] = value.toFloat()
    }

    override fun dump(row: Int) = data[row].toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is FloatColumn -> false
        else -> label == other.label && Arrays.equals(data, other.data)
    }

    override fun hashCode() = Arrays.deepHashCode(arrayOf(label, data))

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", Arrays.toString(data))
            .toString()
}

@Suppress("platform_class_mapped_to_kotlin") class BooleanColumn(label: String, data: BitterSet) :
        Column<BitterSet>(label, data, Boolean::class.java) {
    // XXX ^^^ can't get .javaClass to work :(

    override fun load(row: Int, value: String) {
        data[row] = value == "1" || value.equals("true", ignoreCase = true)
    }

    override fun getAsDouble(row: Int) = if (data[row]) 1.0 else 0.0

    override fun wrap(newData: BitterSet): Column<BitterSet> {
        return BooleanColumn(label, newData)
    }

    override fun rename(newLabel: String): Column<BitterSet> {
        return BooleanColumn(newLabel, data)
    }

    override fun reorder(indices: IntArray): Column<BitterSet> {
        val length = indices.size
        require(size() == length)

        return wrap(BitterSet.of(length) { i -> data[indices[i]] })
    }

    override fun resize(newSize: Int): Column<BitterSet> {
        val currSize = size()
        val totalSize = currSize + newSize
        val content = data.peel()

        // resize if needed
        if (totalSize > currSize) {
            content.set(totalSize, false)
        }
        return wrap(BitterSet.of(totalSize, content))
    }

    override fun plus(other: Column<*>)
            = wrap(data + (other as BooleanColumn).data)

    @Deprecated("")
    override fun merge(other: Column<*>): Column<BitterSet> {
        throw UnsupportedOperationException()
    }

    override fun filter(mask: BitSet): Column<BitterSet> {
        val numRows = mask.cardinality()

        val filtered = BitterSet(numRows)
        var offset = 0
        for (i in 0..size() - 1) {
            if (mask.get(i)) {
                filtered.set(offset++, data.get(i))
            }
        }
        return wrap(filtered)
    }

    override fun sorted(order: SortOrder): IntArray {
        val rowsNumber = size()

        val indices = IntArray(rowsNumber)
        val firstValue = order !== SortOrder.ASC

        var offset = 0
        // fill first values
        for (row in 0..rowsNumber - 1) {
            if (data[row] == firstValue) {
                indices[offset++] = row
            }
        }
        // fill second values
        for (row in 0..rowsNumber - 1) {
            if (data[row] != firstValue) {
                indices[offset++] = row
            }
        }

        return indices
    }

    @Deprecated("")
    override fun intersect(c: Column<*>): ObjIntPredicate<BitterSet> {
        throw UnsupportedOperationException()
    }

    override fun size() = data.size()

    override fun dump(row: Int): String = if (data[row]) "1" else "0"

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is BooleanColumn -> false
        else -> label == other.label && data == other.data
    }

    override fun hashCode() = Objects.hash(label, data)

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("label", label)
            .add("data", data)
            .toString()
}
