package com.khizrim.dialer.callerid

import java.io.DataInputStream
import java.io.InputStream

/**
 * Офлайн-реестр российской нумерации (Минцифры): диапазон номеров → оператор и регион.
 * Файл собирает tools/callerid/build_db.py. Поиск: двоичный по блокам, внутри блока подряд.
 */
class NumberDb private constructor(
    val updated: String,
    private val operators: Array<String>,
    private val operatorFlags: ByteArray,
    private val places: Array<String>,
    private val tables: List<Table>,
) {
    data class Hit(val mobile: Boolean, val operator: String, val virtual: Boolean, val region: String)

    private class Table(
        val mobile: Boolean,
        val count: Int,
        val blockSize: Int,
        val prevEnds: LongArray,
        val firstStarts: LongArray,
        val offsets: IntArray,
        val payload: ByteArray,
    )

    val size: Int get() = tables.sumOf { it.count }

    fun find(national: Long): Hit? {
        for (t in tables) {
            val block = lastBlockAtOrBefore(t.firstStarts, national)
            if (block < 0) continue
            val r = Reader(t.payload, t.offsets[block])
            var prev = t.prevEnds[block]
            val records = minOf(t.blockSize, t.count - block * t.blockSize)
            for (i in 0 until records) {
                val start = prev + 1 + r.varint()
                val end = start + r.varint()
                val op = r.varint().toInt()
                val place = r.varint().toInt()
                if (national < start) break
                if (national <= end) {
                    return Hit(t.mobile, operators[op], operatorFlags[op].toInt() and FLAG_VIRTUAL != 0, places[place])
                }
                prev = end
            }
        }
        return null
    }

    private class Reader(private val bytes: ByteArray, private var pos: Int) {
        fun varint(): Long {
            var v = 0L
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                v = v or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return v
                shift += 7
            }
        }
    }

    companion object {
        private const val FLAG_VIRTUAL = 1

        private fun lastBlockAtOrBefore(starts: LongArray, value: Long): Int {
            var lo = 0; var hi = starts.size - 1; var ans = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (starts[mid] <= value) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            return ans
        }

        fun read(input: InputStream): NumberDb {
            val d = DataInputStream(input.buffered(64 * 1024))
            val magic = ByteArray(6).also { d.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "OZCID1") { "not a caller id db" }
            val updated = d.readUTF()
            val nOps = d.readUnsignedShort()
            val ops = Array(nOps) { "" }
            val flags = ByteArray(nOps)
            for (i in 0 until nOps) { ops[i] = d.readUTF(); flags[i] = d.readByte() }
            val nPlaces = d.readUnsignedShort()
            val places = Array(nPlaces) { d.readUTF() }
            val nTables = d.readUnsignedByte()
            val tables = List(nTables) {
                val type = d.readUnsignedByte()
                val count = d.readInt()
                val blockSize = d.readInt()
                val nBlocks = d.readInt()
                val prevs = LongArray(nBlocks); val firsts = LongArray(nBlocks); val offs = IntArray(nBlocks)
                for (b in 0 until nBlocks) { prevs[b] = d.readLong(); firsts[b] = d.readLong(); offs[b] = d.readInt() }
                val payload = ByteArray(d.readInt()).also { d.readFully(it) }
                Table(type == 0, count, blockSize, prevs, firsts, offs, payload)
            }
            return NumberDb(updated, ops, flags, places, tables)
        }
    }
}
