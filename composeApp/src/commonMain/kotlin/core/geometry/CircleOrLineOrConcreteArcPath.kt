package core.geometry

sealed interface CircleOrLineOrConcreteArcPath
    : GCircleOrConcreteAcPath, Region, LocusWithOrder, Intersectable