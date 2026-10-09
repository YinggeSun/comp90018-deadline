package com.comp90018.deadline.core.util

import java.text.BreakIterator

/**
 * The longest prefix of whole user-perceived characters (an emoji, or a letter with its
 * accents) whose UTF-16 length is at most [maxLength]. Cutting by `take` instead could
 * split an emoji into an invalid half that changes when stored and read back.
 */
fun String.takeWholeCharacters(maxLength: Int): String {
    if (length <= maxLength) return this
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(this)
    var end = 0
    var next = boundaries.next()
    while (next != BreakIterator.DONE && next <= maxLength) {
        end = next
        next = boundaries.next()
    }
    return substring(0, end)
}
