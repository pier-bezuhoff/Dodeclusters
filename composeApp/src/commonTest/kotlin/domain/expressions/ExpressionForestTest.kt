package domain.expressions

import compareScenarios
import domain.Ix
import kotlin.random.Random
import kotlin.test.Test

class ExpressionForestTest {

    private fun makeForest(size: Int): Map<Ix, ConformalExprOutput?> {
        val forest = mutableMapOf<Ix, ConformalExprOutput?>()
        val nFree = maxOf(size.div(50), 3)
        forest += (0 until nFree).map { ix -> ix to null }
        repeat(size - nFree) {
            val i = forest.size
            val r = Random.nextInt()
            when {
                r % 3 == 0 -> {
                    val parent = Random.nextInt(forest.size)
                    forest[i] = ExprOutput.Just(
                        Expr.Incidence(IncidenceParameters(0.0), parent)
                    )
                }
                r % 2 == 0 -> {
                    val parent1 = Random.nextInt(forest.size)
                    val parent2 = Random.nextInt(forest.size)
                    forest[i] = ExprOutput.Just(
                        Expr.LineBy2Points(parent1, parent2)
                    )
                }
                else -> {
                    val parent1 = Random.nextInt(forest.size)
                    val parent2 = Random.nextInt(forest.size)
                    val parent3 = Random.nextInt(forest.size)
                    forest[i] = ExprOutput.OneOf(
                        Expr.LoxodromicMotion(
                            LoxodromicMotionParameters(0f, 0.0, 1),
                            parent1, parent2, parent3
                        ), 0
                    )
                }
            }
        }
        return forest
    }

    @Test
    fun `performance of getAllParents`() {
        val size = 100
        val forest = ExpressionForest(
            makeForest(size),
            MutableList(size) { null }
        )
        println()
        compareScenarios(
            "while + sets", {
//                forest.getAllParents(
//                    listOf(Random.nextInt(size))
//                )
            },
            "while + stack", {
//                forest._getAllParents(
//                    listOf(Random.nextInt(size))
//                )
            },
            nRuns = 100_000
        )
        println("______________________________________________________")
        repeat(1000) {
            val l = listOf(Random.nextInt(size))
//            assertEquals(forest.getAllParents(l), forest._getAllParents(l))
        }
    }

    @Test
    fun `performance of getAllChildren`() {
        val size = 1000
        val forest = ExpressionForest(
            makeForest(size),
            MutableList(size) { null }
        )
        println()
        compareScenarios(
            "rec", {
//                forest.getAllChildren(
//                    Random.nextInt(size)
//                )
            },
            "while + stack", { // ~3 times faster
//                forest._getAllChildren(
//                    Random.nextInt(size)
//                )
            },
            nRuns = 10_000
        )
        println("______________________________________________________")
        repeat(1000) {
            val i = Random.nextInt(size)
//            assertEquals(forest.getAllChildren(i), forest._getAllChildren(i))
        }
    }
}