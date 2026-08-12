package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import domain.Ix
import domain.PointSnapResult
import domain.expressions.ArcPath
import domain.expressions.computeSagittaRatio
import domain.model.PartialArcPath

class PartialArcPathManager(
    partialArcPathState: MutableState<PartialArcPath?>,
) {
    private var partialArcPath: PartialArcPath? by partialArcPathState

    context(viewModel: EditorViewModel)
    fun downArcPathPoint(absolutePosition: Offset) {
        val snap = viewModel.snapped(absolutePosition)
        val arcPath = partialArcPath
        partialArcPath = if (arcPath == null) {
            PartialArcPath(
                vertices = listOf(PartialArcPath.Vertex(snap)),
                focus = PartialArcPath.Focus.Vertex(0),
            )
        } else {
            val vertexIndex = arcPath.vertices.indexOfFirst { vertex ->
                viewModel.isCloseEnoughToSelect(vertex.point.toOffset(), absolutePosition)
            }
            if (vertexIndex != -1) {
                arcPath.copy(focus = PartialArcPath.Focus.Vertex(vertexIndex))
            } else {
                val arcIndex = arcPath.arcs.indexOfFirst { arc ->
                    viewModel.isCloseEnoughToSelect(arc.middlePoint.toOffset(), absolutePosition)
                }
                if (arcIndex != -1) {
                    arcPath.copy(focus = PartialArcPath.Focus.MidPoint(arcIndex))
                } else {
                    arcPath.addNewVertexAndGrabIt(PartialArcPath.Vertex(snap))
                }
            }
        }
    }

    context(viewModel: EditorViewModel)
    fun tapDuringArcPathToolMode(absolutePosition: Offset) {
        val pArcPath = partialArcPath
        if (pArcPath != null && !pArcPath.isClosed && pArcPath.vertices.size >= 2 &&
            viewModel.isCloseEnoughToSelect(
                pArcPath.vertices.first().point.toOffset(),
                absolutePosition,
            )
        ) {
            partialArcPath = pArcPath.connectLastToFirst()
//            viewModel.showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
        }
    }

    context(viewModel: EditorViewModel)
    fun updatePartialArcPathFocus(absolutePosition: Offset) {
        val snap = viewModel.snapped(absolutePosition)
        partialArcPath = partialArcPath?.moveFocus(snap, snapDistance = viewModel.tapRadius.toDouble())
    }

    context(viewModel: EditorViewModel)
    fun upPartialArcPath(absolutePosition: Offset?) {
        var pArcPath = partialArcPath?.realignGrabbedMidpoint()
        val focus = pArcPath?.focus
        // attempt fusing focused vertex to the next or previous
        if (pArcPath != null && absolutePosition != null && focus is PartialArcPath.Focus.Vertex) {
            val closeVertices = pArcPath.vertices.indices
                .minus(focus.vertexIndex)
                .filter { i ->
                    absolutePosition.minus(pArcPath.vertices[i].point.toOffset())
                        .getDistanceSquared() <= viewModel.tapRadius2
                }.toSet()
            val nextVertexIndex = (focus.vertexIndex + 1).mod(pArcPath.vertices.size)
            val previousVertexIndex = (focus.vertexIndex - 1).mod(pArcPath.vertices.size)
            when {
                nextVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(focus.vertexIndex)
//                    if (partialArcPath?.isClosed == false && pArcPath.isClosed)
//                        showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
                }
                previousVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(previousVertexIndex)
//                    if (partialArcPath?.isClosed == false && pArcPath.isClosed)
//                        showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
                }
                else -> {
                    // we can also snap 2 non-neighboring vertices, but it's prob not a good idea
                }
            }
        }
        partialArcPath = pArcPath
    }

    context(viewModel: EditorViewModel)
    fun completeArcPath() {
        val pArcPath = partialArcPath ?: return
//        println(pArcPath)
        val vertexIndices: List<Ix> = pArcPath.vertices.map { vertex ->
            when (val p2p = viewModel.realizePointSnap(vertex.snap, recordHistory = false)) {
                is PointSnapResult.Eq -> p2p.pointIndex
                is PointSnapResult.Free -> viewModel.createNewFreePoint(p2p.result)
            }
        }
        val arcs = pArcPath.arcs.mapIndexed { arcIndex, arc ->
            when (val p2p = viewModel.realizePointSnap(arc.midpointSnap, recordHistory = false)) {
                is PointSnapResult.Free -> {
                    ArcPath.Arc.By2Points(sagittaRatio =
                        if (arc.circle == null)
                            0.0 // straight line
                        else
                            computeSagittaRatio(
                                circle = arc.circle,
                                chordStart = pArcPath.arcIndex2startVertex(arcIndex).point,
                                chordEnd = pArcPath.arcIndex2endVertex(arcIndex).point,
                            )
                    )
                }
                is PointSnapResult.Eq -> {
                    ArcPath.Arc.By3Points(middlePointIndex = p2p.pointIndex)
                }
            }
        }
        val concreteArcPath = viewModel.expressions.addSoloExpr(
            if (pArcPath.isClosed)
                ArcPath.Closed(vertices = vertexIndices, arcs = arcs)
            else
                ArcPath.Open(vertices = vertexIndices, arcs = arcs)
        )
        val ix = viewModel.objectModel.addDownscaledObject(concreteArcPath)
        // TODO: init SubMode.ToolResultPostprocessing
        viewModel.objectModel.invalidate()
        viewModel.recordHistory()
        partialArcPath = null
    }

}