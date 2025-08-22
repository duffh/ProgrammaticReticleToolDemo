/* Copyright 2025 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.example.programmaticreticletooldemo.components

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arcgismaps.Color
import com.arcgismaps.geometry.Envelope
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryType
import com.arcgismaps.geometry.Multipart
import com.arcgismaps.geometry.Multipoint
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.Polygon
import com.arcgismaps.geometry.Polyline
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.mapping.symbology.SimpleFillSymbol
import com.arcgismaps.mapping.symbology.SimpleFillSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbol
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbolStyle
import com.arcgismaps.mapping.symbology.Symbol
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.LongPressEvent
import com.arcgismaps.mapping.view.ScreenCoordinate
import com.arcgismaps.mapping.view.SingleTapConfirmedEvent
import com.arcgismaps.mapping.view.geometryeditor.GeometryEditor
import com.arcgismaps.mapping.view.geometryeditor.GeometryEditorElement
import com.arcgismaps.mapping.view.geometryeditor.GeometryEditorMidVertex
import com.arcgismaps.mapping.view.geometryeditor.GeometryEditorVertex
import com.arcgismaps.mapping.view.geometryeditor.ProgrammaticReticleTool
import com.arcgismaps.toolkit.geoviewcompose.MapViewProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val Color.Companion.blue
    get() = fromRgba(0, 0, 255, 255)

private val Color.Companion.orangeRed
    get() = fromRgba(128, 128, 0, 255)

private val Color.Companion.transparentRed
    get() = fromRgba(255, 0, 0, 70)

class ProgrammaticReticleToolDemoViewModel(app: Application) : AndroidViewModel(app) {

    private var reticleState = ReticleState.Default
    private var selectedGraphic: Graphic? = null
    private val programmaticReticleTool = ProgrammaticReticleTool()

    private val _allowVertexCreation = MutableStateFlow(true)
    val allowVertexCreation: StateFlow<Boolean> = _allowVertexCreation

    val arcGISMap = ArcGISMap(BasemapStyle.ArcGISDarkGray).apply {
        initialViewpoint = Viewpoint(20.798363, -156.331924, 1e6)
    }

    val geometryEditor = GeometryEditor().apply {
        tool = programmaticReticleTool
    }

    val mapViewProxy = MapViewProxy()

    private val _contextActionButtonText = MutableStateFlow("")
    val contextActionButtonText: StateFlow<String> = _contextActionButtonText

    private val _contextActionButtonEnabled = MutableStateFlow(true)
    val contextActionButtonEnabled: StateFlow<Boolean> = _contextActionButtonEnabled

    private val _showContextMenu = MutableStateFlow(false)
    val showContextMenu: StateFlow<Boolean> = _showContextMenu

    private val _contextMenuPosition = MutableStateFlow(Offset(0f, 0f))
    val contextMenuPosition: StateFlow<Offset> = _contextMenuPosition

    private val _contextMenuElement = MutableStateFlow<GeometryEditorElement?>(null)
    val contextMenuElement: StateFlow<GeometryEditorElement?> = _contextMenuElement

    private val graphicsOverlay = GraphicsOverlay()
    val graphicsOverlays = listOf(graphicsOverlay)

    // True if there is a selected element which is allowed to be deleted.
    val canDeleteSelectedElement = geometryEditor.selectedElement.map { selectedElement ->
        selectedElement?.canDelete == true
    }

    // True if either there is a picked up element or the editor's `canUndo` is true.
    val canUndoOrCancelInteraction = geometryEditor.pickedUpElement.map { it != null }
        .combineTransform(geometryEditor.canUndo) { a, b ->
            emit(a || b)
        }

    init {
        viewModelScope.run {
            launch {
                arcGISMap.load()
            }
            launch {
                geometryEditor.pickedUpElement.collect {
                    updateReticleState()
                }
            }
            launch {
                geometryEditor.hoveredElement.collect {
                    updateReticleState()
                }
            }
        }

        UpdateContextActionButtonState() // set initial 'start editor' button text
    }

    fun onMapViewTap(tapEvent: SingleTapConfirmedEvent) {
        viewModelScope.launch {
            if (geometryEditor.isStarted.value) {
                selectAndSetViewpointAt(tapEvent.screenCoordinate)
            } else {
                startWithIdentifiedGeometry(tapEvent.screenCoordinate)
            }
            _showContextMenu.value = false
        }
    }

    fun onMapViewLongPress(longPressEvent: LongPressEvent) {
        viewModelScope.launch {
            if (geometryEditor.isStarted.value) {
                showContextMenuAt(longPressEvent.screenCoordinate)
            }
        }
    }

    fun onVisibleAreaChanged(polygon: Polygon) {
        _showContextMenu.value = false
    }

    /**
     * Performs different actions based on the editor's current hovered and picked up element, as
     * well as whether vertex creation is allowed. The behaviour is as follows:
     * - If the editor is stopped, starts it with a new geometry with the currently-selected geometry type
     * - Otherwise, if there is a picked up element, places it under the reticle
     * - Otherwise, if a vertex or mid-vertex is hovered over, picks it up
     * - Otherwise, inserts a new vertex at the reticle position
     */
    fun onContextActionButtonClick() {
        if (!geometryEditor.isStarted.value) {
            geometryEditor.start(GeometryType.Polygon)
            updateReticleState()
            return
        }

        when (reticleState) {
            ReticleState.Default, ReticleState.PickedUp -> programmaticReticleTool.placeElementAtReticle()
            ReticleState.HoveringVertex, ReticleState.HoveringMidVertex -> {
                programmaticReticleTool.selectElementAtReticle()
                programmaticReticleTool.pickUpSelectedElement()
            }
        }
    }

    /**
     * Set the viewpoint to the position of the element associated with the context menu.
     */
    fun onContextMenuZoomClick() {
        viewModelScope.launch {
            val element = _contextMenuElement.value
            if (element is GeometryEditorVertex || element is GeometryEditorMidVertex) {
                _showContextMenu.value = false
                mapViewProxy.setViewpointAnimated(
                    Viewpoint(element.extent),
                    300.toDuration(
                        DurationUnit.MILLISECONDS
                    )
                )
            }
            _contextMenuElement.value = null
        }
    }

    /**
     * Delete the element associated with the context menu if it is a vertex.
     */
    fun onContextMenuDeleteClick() {
        viewModelScope.launch {
            val element = _contextMenuElement.value
            if (element is GeometryEditorVertex) {
                geometryEditor.selectVertex(element.partIndex, element.vertexIndex)
                geometryEditor.deleteSelectedElement()
            }
        }
        _contextMenuElement.value = null
        _showContextMenu.value = false
    }


    fun onCancelButtonClick() {
        geometryEditor.stop()
        resetExistingEditState()
    }

    fun onDoneButtonClick() {
        val geometry = geometryEditor.stop()
        if (geometry != null) {
            val selectedGraphic = selectedGraphic
            if (selectedGraphic != null) {
                selectedGraphic.geometry = geometry
            } else {
                graphicsOverlay.graphics.add(
                    Graphic(
                        geometry = geometry,
                        symbol = geometry.defaultSymbol
                    )
                )
            }
        }
        resetExistingEditState()
    }

    fun onRedoButtonPressed() {
        geometryEditor.redo()
    }

    /**
     * If there is a picked up element, places the element back in its original position.
     * Otherwise, undoes the last geometry editor action, if any.
     */
    fun onUndoButtonPressed() {
        if (geometryEditor.pickedUpElement.value != null) {
            geometryEditor.cancelCurrentAction()
        } else {
            geometryEditor.undo()
        }
    }

    fun onDeleteButtonPressed() {
        geometryEditor.deleteSelectedElement()
    }

    /**
     * Enables or disables vertex creation, which affects the feedback lines and vertices, the
     * allowed context-button actions, and the presence of the grow effect for mid-vertices.
     */
    fun setAllowVertexCreation(newValue: Boolean) {
        _allowVertexCreation.value = newValue
        programmaticReticleTool.vertexCreationPreviewEnabled = newValue
        // Picking up a mid-vertex will lead to a new vertex being created. Only show feedback for
        // this if vertex creation is enabled.
        programmaticReticleTool.style.growEffect?.applyToMidVertices = newValue
        UpdateContextActionButtonState()
    }

    private fun resetExistingEditState() {
        selectedGraphic?.let { selectedGraphic ->
            selectedGraphic.isSelected = false
            selectedGraphic.isVisible = true
        }
        selectedGraphic = null
        UpdateContextActionButtonState()
    }

    /**
     * Called whenever something happens that may change what the context-action button does
     * (e.g. editor stopping/starting, hovered or picked-up element changing).
     *
     * The private [reticleState] property decides what happens when the context-action button
     * is pressed, and is used to derive the text and enabled-ness of the button.
     */
    private fun updateReticleState() {
        if (geometryEditor.pickedUpElement.value != null) {
            reticleState = ReticleState.PickedUp
            UpdateContextActionButtonState()
            return
        }

        reticleState = when (geometryEditor.hoveredElement.value) {
            is GeometryEditorVertex -> ReticleState.HoveringVertex
            is GeometryEditorMidVertex -> ReticleState.HoveringMidVertex
            else -> ReticleState.Default
        }

        UpdateContextActionButtonState()
    }

    /**
     * Sets the text and enabled-ness of the context-action button based on the current reticle
     * state as well as whether vertex creation is allowed.
     *
     * Note that the enabled-ness of the button is used to prevent vertex insertion when vertex
     * creation is disabled.
     */
    private fun UpdateContextActionButtonState() {
        if (!geometryEditor.isStarted.value) {
            _contextActionButtonText.value = "Start Geometry Editor"
            _contextActionButtonEnabled.value = true
            return
        }

        if (allowVertexCreation.value) {
            _contextActionButtonEnabled.value = true
            _contextActionButtonText.value = when (reticleState) {
                ReticleState.Default -> "Insert point"
                ReticleState.PickedUp -> "Drop point"
                ReticleState.HoveringVertex, ReticleState.HoveringMidVertex -> "Pick up point"
            }
        } else {
            _contextActionButtonText.value = when (reticleState) {
                ReticleState.PickedUp -> "Drop point"
                ReticleState.Default, ReticleState.HoveringVertex, ReticleState.HoveringMidVertex -> "Pick up point"
            }
            _contextActionButtonEnabled.value =
                reticleState == ReticleState.HoveringVertex || reticleState == ReticleState.PickedUp
        }
    }

    /**
     * Displays a context menu at the interacted position if a vertex or mid-vertex are long pressed.
     */
    private suspend fun showContextMenuAt(tapPosition: ScreenCoordinate) {
        val identifyResult =
            mapViewProxy.identifyGeometryEditor(tapPosition, tolerance = 15.dp).getOrNull()
                ?: return
        val topElement = identifyResult.elements.firstOrNull() ?: return
        if (topElement is GeometryEditorVertex || topElement is GeometryEditorMidVertex) {
            _showContextMenu.value = true
            _contextMenuPosition.value = Offset(tapPosition.x.toFloat(), tapPosition.y.toFloat())
            _contextMenuElement.value = topElement
        } else {
            _showContextMenu.value = false
        }
    }

    /**
     * Identifies for a geometry editor element at the given screen coordinates. If a vertex
     * or mid-vertex is found, selects it and centers it in the view.
     */
    private suspend fun selectAndSetViewpointAt(tapPosition: ScreenCoordinate) {
        val identifyResult =
            mapViewProxy.identifyGeometryEditor(tapPosition, tolerance = 15.dp).getOrNull()
                ?: return
        val topElement = identifyResult.elements.firstOrNull() ?: return
        val duration = 300.toDuration(DurationUnit.MILLISECONDS)
        when (topElement) {
            is GeometryEditorVertex -> {
                geometryEditor.selectVertex(topElement.partIndex, topElement.vertexIndex)
                mapViewProxy.setViewpointAnimated(Viewpoint(topElement.point), duration)
            }

            is GeometryEditorMidVertex -> {
                if (allowVertexCreation.value) {
                    geometryEditor.selectMidVertex(topElement.partIndex, topElement.segmentIndex)
                    mapViewProxy.setViewpointAnimated(Viewpoint(topElement.point), duration)
                }
            }

            else -> { /* Only zoom to vertices and mid-vertices. */
            }
        }
    }

    /**
     * Identifies for an existing graphic in the graphic overlay. If found, starts the geometry editor
     * using the graphic's geometry. Hides the existing graphic for the duration of the edit session.
     * Sets a new viewpoint center based on the graphic position (depending on what type of edits
     * are allowed).
     */
    private suspend fun startWithIdentifiedGeometry(tapPosition: ScreenCoordinate) {
        val identifyResult =
            mapViewProxy.identify(graphicsOverlay, tapPosition, tolerance = 15.dp).getOrNull()
                ?: return
        val graphic = identifyResult.graphics.firstOrNull() ?: return
        geometryEditor.start(graphic.geometry ?: return)

        graphic.geometry?.let { geometry ->
            if (allowVertexCreation.value) {
                mapViewProxy.setViewpointCenter(geometry.extent.center)
            } else {
                geometry.lastPoint?.let { lastPoint ->
                    mapViewProxy.setViewpointCenter(lastPoint)
                }
            }
        }

        graphic.isSelected = true
        graphic.isVisible = false
        selectedGraphic = graphic
        updateReticleState()
    }

    private enum class ReticleState {
        Default,
        PickedUp,
        HoveringVertex,
        HoveringMidVertex,
    }
}

/**
 * A default symbol for the given geometry.
 */
private val Geometry.defaultSymbol: Symbol
    get() = when (this) {
        is Envelope -> throw IllegalStateException("Envelopes not supported by the geometry editor.")
        is Polygon -> SimpleFillSymbol(
            SimpleFillSymbolStyle.Solid,
            Color.transparentRed,
            outline = SimpleLineSymbol(SimpleLineSymbolStyle.Dash, Color.black, 1f)
        )

        is Polyline -> SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.blue, 2f)
        is Multipoint -> SimpleMarkerSymbol(SimpleMarkerSymbolStyle.Circle, Color.yellow, 5f)
        is Point -> SimpleMarkerSymbol(SimpleMarkerSymbolStyle.Square, Color.orangeRed, 10f)
    }

/**
 * The last point of the first part of the given geometry (assumes there is at least one point).
 */
private val Geometry.lastPoint: Point?
    get() = when (this) {
        is Envelope -> throw IllegalStateException("Envelopes not supported by the geometry editor.")
        is Multipart -> parts.firstOrNull()?.endPoint
        is Point -> this
        is Multipoint -> points.lastOrNull()
    }