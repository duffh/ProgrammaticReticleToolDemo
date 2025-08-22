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

package com.example.programmaticreticletooldemo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcgismaps.toolkit.geoviewcompose.MapView
import com.example.programmaticreticletooldemo.R
import com.example.programmaticreticletooldemo.components.ProgrammaticReticleToolDemoViewModel
import com.example.programmaticreticletooldemo.ui.theme.BlueButtonBackground
import com.example.programmaticreticletooldemo.ui.theme.BlueButtonText

@Composable
fun MainScreen() {
    val mapViewModel: ProgrammaticReticleToolDemoViewModel = viewModel()
    Scaffold(
        content = {
            val allowVertexCreation by mapViewModel.allowVertexCreation.collectAsState()
            val contextActionButtonEnabled by mapViewModel.contextActionButtonEnabled.collectAsState()
            val contextActionButtonText by mapViewModel.contextActionButtonText.collectAsState()
            val isEditorStarted by mapViewModel.geometryEditor.isStarted.collectAsState()
            var isSettingsVisible by remember { mutableStateOf(false) }
            val showContextMenu by mapViewModel.showContextMenu.collectAsState()
            val contextMenuPosition by mapViewModel.contextMenuPosition.collectAsState()
            val contextMenuElement by mapViewModel.contextMenuElement.collectAsState()
            val canDeleteSelectedElement by mapViewModel.canDeleteSelectedElement.collectAsState(
                initial = false
            )
            val canUndo by mapViewModel.canUndoOrCancelInteraction.collectAsState(initial = false)
            val canRedo by mapViewModel.geometryEditor.canRedo.collectAsState()

            if (isSettingsVisible) {
                SettingsScreen(
                    isEditorStarted = isEditorStarted,
                    canDeleteSelectedElement = canDeleteSelectedElement,
                    allowVertexCreation = allowVertexCreation,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onVertexCreationToggled = mapViewModel::setAllowVertexCreation,
                    onUndoButtonPressed = mapViewModel::onUndoButtonPressed,
                    onRedoButtonPressed = mapViewModel::onRedoButtonPressed,
                    onDeleteButtonPressed = mapViewModel::onDeleteButtonPressed,
                    onDismissRequest = { isSettingsVisible = false }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            ) {
                Row(modifier = Modifier.height(50.dp)) {
                    if (isEditorStarted) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth(0.33f)
                                .padding(2.dp),
                            onClick = mapViewModel::onCancelButtonClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                            ),
                        ) {
                            Text(text = "Cancel", color = BlueButtonText)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                modifier = Modifier.padding(0.dp, 14.dp, 0.dp, 0.dp),
                                text = "Edit geometry",
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                            ),
                            onClick = mapViewModel::onDoneButtonClick,
                        ) {
                            Text(text = "Done", color = BlueButtonText)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                modifier = Modifier.padding(0.dp, 14.dp, 0.dp, 0.dp),
                                text = "Edit geometry",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                MapView(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    arcGISMap = mapViewModel.arcGISMap,
                    mapViewProxy = mapViewModel.mapViewProxy,
                    graphicsOverlays = mapViewModel.graphicsOverlays,
                    geometryEditor = mapViewModel.geometryEditor,
                    onSingleTapConfirmed = mapViewModel::onMapViewTap,
                    onLongPress = mapViewModel::onMapViewLongPress,
                    onVisibleAreaChanged = mapViewModel::onVisibleAreaChanged,
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        FloatingActionButton(
                            onClick = { isSettingsVisible = true },
                            modifier = Modifier.padding(16.dp),
                            containerColor = BlueButtonBackground,
                        )
                        {
                            Icon(
                                painter = painterResource(id = R.drawable.gear_24),
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }
                if (showContextMenu) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(
                            contextMenuPosition.x.toInt(),
                            contextMenuPosition.y.toInt()
                        )
                    ) {
                        ElevatedCard(
                            modifier = Modifier.size(width = 120.dp, height = 110.dp),
                            shape = RoundedCornerShape(10.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 6.dp
                            ),
                        )
                        {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.SpaceEvenly, // Space buttons evenly
                                horizontalAlignment = Alignment.CenterHorizontally // Center buttons horizontally
                            )
                            {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(2.dp),
                                    onClick = mapViewModel::onContextMenuZoomClick,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "Zoom")
                                    }
                                }
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(2.dp),
                                    onClick = mapViewModel::onContextMenuDeleteClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Red,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = contextMenuElement?.canDelete == true

                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxHeight(0.1f), contentAlignment = Alignment.Center) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f)
                            .padding(6.dp),
                        onClick = mapViewModel::onContextActionButtonClick,
                        enabled = contextActionButtonEnabled,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = contextActionButtonText)
                    }
                }
            }
        }
    )
}
