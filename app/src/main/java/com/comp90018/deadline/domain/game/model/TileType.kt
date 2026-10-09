package com.comp90018.deadline.domain.game.model

enum class TileType {
    DEFAULT,
    BOOK,
    COFFEE,
    LAPTOP,
    ASSIGNMENT,
    QUIZ,
    READING,

    /** Will also ease Stress when matched; the effect is added separately (#100). */
    MUSIC,
    EXAM,
    PRESENTATION,
    LECTURE_SLIDE,
}
