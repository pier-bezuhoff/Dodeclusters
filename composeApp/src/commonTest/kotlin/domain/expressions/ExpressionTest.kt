package domain.expressions

import data.geometry.Circle
import data.geometry.GCircle
import data.geometry.Point
import domain.cluster.Constellation
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ExpressionTest {
    enum class Variation {
        V1, V2, V3, V4
    }

    val constellation = Constellation(
        listOf(
            ObjectConstruct.ConcretePoint(Point(0.0, 200.0)),
            ObjectConstruct.ConcretePoint(Point(120.0, 90.0)),
            ObjectConstruct.ConcretePoint(Point(200.0, 0.0)),
            ObjectConstruct.Dynamic(Expression.Just(Expr.CircleBy3Points(0, 1, 2))),
            ObjectConstruct.ConcreteCircle(Circle(100.0, 100.0, 50.0)),
            ObjectConstruct.Dynamic(Expression.OneOf(Expr.Intersection(3, 4), 0)), // 5
            ObjectConstruct.Dynamic(Expression.OneOf(Expr.Intersection(3, 4), 1)), // 6
            ObjectConstruct.ConcreteCircle(Circle(300.0, 300.0, 20.0)), // 7
        ) + (0..19).map { k ->
            ObjectConstruct.Dynamic(Expression.OneOf(Expr.LoxodromicMotion(LoxodromicMotionParameters(360f, 4.0, 20), 5, 6, 7), k))
        } // 8 .. 28
        ,
        emptyList()
    )

    @Test
    fun performanceTest() {
        val objects = mutableListOf<GCircle?>()
        objects.addAll(
            constellation.objects.map {
                when (it) {
                    is ObjectConstruct.ConcreteCircle -> it.circle
                    is ObjectConstruct.ConcreteLine -> it.line
                    is ObjectConstruct.ConcretePoint -> it.point
                    is ObjectConstruct.Dynamic -> null // to-be-computed during reEval()
                }
            }
        )
        val ds = 1.0/200.0
//        val ast = ExpressionForest(
//            initialExpressions = constellation.toExpressionMap(),
//            get = { objects[it]?.scaled(0.0, 0.0, ds)
//            },
//            set = { ix, value ->
//                objects[ix] = value?.scaled(0.0, 0.0, 200.0)
//            }
//        )
//        ast.reEval()
        println(objects)
        /*
        val variationTimes: MutableMap<Variation, Duration> = mutableMapOf(
            Variation.V1 to Duration.ZERO,
            Variation.V2 to Duration.ZERO,
            Variation.V3 to Duration.ZERO,
            Variation.V4 to Duration.ZERO,
        )
        repeat(10_000) {
            val variation = Random.nextInt().let { k ->
                when (abs(k) % 4) {
                    1 -> Variation.V1
                    2 -> Variation.V2
                    3 -> Variation.V3
                    0 -> Variation.V4
                    else -> TODO()
                }
            }
            variationTimes[variation] = variationTimes[variation]!! + when (variation) {
                Variation.V1 -> measureTime {
                    repeat(10) {
                        ast.reEval()
                    }
                }
                Variation.V2 -> measureTime {
                    repeat(10) {
                        ast._reEval(objects)
                    }
                }
                Variation.V3 -> measureTime {
                    repeat(10) {
                        ast.__reEval(objects)
                    }
                }
                Variation.V4 -> measureTime {
                }
            }
        }
        println("\nno-inline eval: ${variationTimes[Variation.V1]}")
        println("direct array access: ${variationTimes[Variation.V2]}")
        println("direct array access + downscaling: ${variationTimes[Variation.V3]}")
//        println("inline return: ${variationTimes[Variation.V4]}")

         */
        assertTrue(true)
    }

}
