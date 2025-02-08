package domain

enum class InversionOfControl {
    /** All non-free, non-constrained objects are locked */
    NONE,
    /** You can move dependent objects with all their parents as long as all of the parents are free */
    LEVEL_1,
    /** You can move dependent objects with all their parents */
    LEVEL_INFINITY
}