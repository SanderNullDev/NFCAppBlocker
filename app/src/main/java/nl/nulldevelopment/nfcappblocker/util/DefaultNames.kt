package nl.nulldevelopment.nfcappblocker.util

/** The first of `format(1)`, `format(2)`, ... that isn't in [used], e.g. "Tag 3" after "Tag 1" and "Tag 2". */
fun firstUnusedName(used: Collection<String>, format: (Int) -> String): String {
    val taken = used.toSet()
    return generateSequence(1) { it + 1 }.map(format).first { it !in taken }
}
