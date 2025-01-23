package domain.expressions

import data.geometry.Circle
import data.geometry.GCircle
import data.geometry.Point
import domain.cluster.Constellation
import ui.edit_cluster.EditClusterViewModel
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime

class ExpressionTest {

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

    /*
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
        val ast = ExpressionForest(
            initialExpressions = constellation.toExpressionMap(),
            get = { objects[it]?.scaled(0.0, 0.0, ds)
            },
            set = { ix, value ->
                objects[ix] = value?.scaled(0.0, 0.0, 200.0)
            }
        )
        ast.reEval()
        println(objects)
        measureTime {
            repeat(1000) {
                ast.reEval(objects)
            }
        }.also { println("ref pass: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval()
            }
        }.also { println("get/set: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval(objects)
            }
        }.also { println("ref pass: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval()
            }
        }.also { println("get/set: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval(objects)
            }
        }.also { println("ref pass: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval()
            }
        }.also { println("get/set: $it") }
        measureTime {
            repeat(1000) {
                ast.reEval(objects)
            }
        }.also { println("ref pass: $it") }
        assertTrue(true)
    }
     */

}