package core.geometry

sealed interface CircleOrLineOrConcreteArcPath
    : GCircleOrConcreteArcPath, Region, LocusWithOrder, Intersectable