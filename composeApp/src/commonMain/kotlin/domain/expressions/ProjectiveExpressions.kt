package domain.expressions

import core.geometry.projective.Conic
import domain.Ix

class ProjectiveExpressions(
    initialExpressions: Map<Ix, ProjectiveExprOutput?>, // pls include all possible indices
    objects: MutableList<Conic?>,
) : Expressions<Expr.Projective, Expr.Projective.OneToOne, Expr.Projective.OneToMany, Conic>(
    initialExpressions, objects
) {
    override fun Expr.Projective.evaluate(objects: List<Conic?>): List<Conic?> {
        TODO("not implemented")
    }

    override fun isExprPeriodic(expr: Expr.Projective.OneToMany): Boolean {
        TODO("not implemented")
    }

    override fun adjustIncidentPointExpressions(incidentPointIndices: Collection<Ix>) {
        TODO("not implemented")
    }

    override fun testDependentIncidence(
        pointIndex: Ix,
        carrierIndex: Ix
    ): Boolean {
        TODO("not implemented")
    }
}