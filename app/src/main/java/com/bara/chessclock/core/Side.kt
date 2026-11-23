package com.bara.chessclock.core

/** The two players. The naming is deliberately position-free: side ONE always sits at the bottom. */
enum class Side {
    ONE,
    TWO;

    fun other(): Side = if (this == ONE) TWO else ONE
}
