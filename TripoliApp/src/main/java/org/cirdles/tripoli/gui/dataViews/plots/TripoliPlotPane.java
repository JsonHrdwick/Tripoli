/*
 * Copyright 2022 James Bowring, Noah McLean, Scott Burdick, and CIRDLES.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cirdles.tripoli.gui.dataViews.plots;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.cirdles.tripoli.constants.TripoliConstants;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots.analysisPlots.AnalysisBlockCyclesPlot;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots.analysisPlots.SpeciesIntensityAnalysisPlot;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.tripoliPlots.RatioHistogramPlot;
import org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.tripoliPlots.analysisPlots.AnalysisRatioPlot;
import org.cirdles.tripoli.gui.utilities.TripoliColor;

import static org.cirdles.tripoli.gui.dataViews.plots.PlotWallPane.gridCellDim;
import static org.cirdles.tripoli.gui.dataViews.plots.PlotWallPane.menuOffset;
import static org.cirdles.tripoli.sessions.analysis.Analysis.RUN;

/**
 * @author James F. Bowring
 */
public class TripoliPlotPane extends BorderPane {

    public static double minPlotWidth = 175.0;
    public static double minPlotHeight = 100.0;
    static double mouseStartX;
    static double mouseStartY;
    static boolean onEdgeEast;
    static boolean onEdgeSouth;
    static boolean oneEdgesNorthWest;
    private final EventHandler<MouseEvent> mouseMovedEventHandler = e -> {
        Pane targetPane = (Pane) e.getSource();
        targetPane.setCursor(Cursor.DEFAULT);

        onEdgeEast = (e.getSceneX() >= targetPane.getLayoutX() + targetPane.getWidth() - 2.0)
                && (e.getSceneX() <= targetPane.getLayoutX() + targetPane.getWidth() + 2.0);
        if (onEdgeEast) {
            targetPane.setCursor(Cursor.E_RESIZE);
        }
        onEdgeSouth = (e.getSceneY() - menuOffset >= targetPane.getLayoutY() + targetPane.getHeight() - 2.0)
                && (e.getSceneY() - menuOffset <= targetPane.getLayoutY() + targetPane.getHeight() + 2.0);
        if (onEdgeSouth) {
            targetPane.setCursor(Cursor.S_RESIZE);
        }
        if (onEdgeEast && onEdgeSouth) {
            targetPane.setCursor(Cursor.SE_RESIZE);
        }

        oneEdgesNorthWest =
                ((e.getSceneX() >= targetPane.getLayoutX() - 2.0)
                        && (e.getSceneX() <= targetPane.getLayoutX() + 2.0))
                        ||
                        ((e.getSceneY() - menuOffset >= targetPane.getLayoutY() - 2.0)
                                && (e.getSceneY() - menuOffset <= targetPane.getLayoutY() + 2.0));

        if (oneEdgesNorthWest) {
            targetPane.setCursor(Cursor.OPEN_HAND);
        }
    };
    private double plotToolBarHeight = 0;
    private CheckBox cycleCB;
    private CheckBoxChangeListener cycleCheckBoxChangeListener = new CheckBoxChangeListener(cycleCB);
    private PlotWallPaneInterface plotWallPane;
    private AbstractPlot plot;
    private final EventHandler<MouseEvent> mouseReleasedEventHandler = e -> {
        snapToGrid();
    };
    private final EventHandler<MouseEvent> mouseDraggedEventHandler = e -> {
        Pane targetPane = (Pane) e.getSource();
        double deltaX = e.getSceneX() - mouseStartX;
        double deltaY = e.getSceneY() - mouseStartY;
        if ((deltaX != 0) && (deltaY != 0)) {
            if (oneEdgesNorthWest) {
                if (0.0 < deltaX) {
                    targetPane.setLayoutX(Math.min(targetPane.getLayoutX() + deltaX, ((Pane) plotWallPane).getWidth() - targetPane.getWidth() - gridCellDim));
                } else {
                    targetPane.setLayoutX(Math.max(targetPane.getLayoutX() + deltaX, gridCellDim));
                }

                if (0.0 < deltaY) {
                    targetPane.setLayoutY(Math.min(targetPane.getLayoutY() + deltaY, ((Pane) plotWallPane).getHeight() - targetPane.getHeight() - gridCellDim));
                } else {
                    targetPane.setLayoutY(Math.max(targetPane.getLayoutY() + deltaY, gridCellDim + plotWallPane.getToolBarHeight()));
                }
            }

            if (onEdgeEast) {
                if (0.0 < deltaX) {
                    targetPane.setPrefWidth(Math.min(((Pane) plotWallPane).getWidth() - targetPane.getLayoutX() - gridCellDim, targetPane.getWidth() + deltaX + 0.4));
                } else {
                    targetPane.setPrefWidth(Math.max(minPlotWidth, targetPane.getWidth() + deltaX - 0.6));
                }
            }

            if (onEdgeSouth) {
                if (0.0 < deltaY) {
                    targetPane.setPrefHeight(Math.min(((Pane) plotWallPane).getHeight() - targetPane.getLayoutY() - gridCellDim, targetPane.getHeight() + deltaY + 0.4));
                } else {
                    // shrinking
                    targetPane.setPrefHeight(Math.max(minPlotHeight, targetPane.getHeight() + deltaY - 0.6));
                }
            }

            updatePlot();

            mouseStartX = e.getSceneX();
            mouseStartY = e.getSceneY();
        }
    };
    private PlotLocation plotLocation;
    private final EventHandler<MouseEvent> mouseClickedEventHandler = e -> {
        if (getPlot().mouseInHouse(e.getX(), e.getY())) {
            if (e.isPrimaryButtonDown() && 1 == e.getClickCount()) {
                mouseStartX = e.getSceneX();
                mouseStartY = e.getSceneY();
            }
            if (e.isSecondaryButtonDown() && 2 == e.getClickCount()) {
                if (plot instanceof SpeciesIntensityAnalysisPlot) {

                } else {
                    toggleFullSize();
                }
            }
            toFront();
        }
    };

    private TripoliPlotPane(PlotWallPaneInterface plotWallPane) {
        this.plotWallPane = plotWallPane;
    }

    public static TripoliPlotPane makePlotPane(PlotWallPaneInterface plotWallPane) {
        TripoliPlotPane tripoliPlotPane = new TripoliPlotPane(plotWallPane);

        tripoliPlotPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            tripoliPlotPane.getPlot().setWidthF((Double) newValue);
            tripoliPlotPane.getPlot().refreshPanel(false, false);
        });

        tripoliPlotPane.setLayoutX(0.0);
        tripoliPlotPane.setLayoutY(40.0);
        tripoliPlotPane.initializePlotPane();

        tripoliPlotPane.setStyle(tripoliPlotPane.getStyle() + ";-fx-background-color:RED;");
        ((Pane) plotWallPane).getChildren().addAll(tripoliPlotPane);

        return tripoliPlotPane;
    }

    public AbstractPlot getPlot() {
        return plot;
    }

    private void updatePlot() {
        if (plot != null) {
            plot.updatePlotSize(getPrefWidth(), getPrefHeight() - plotToolBarHeight);
            plot.calculateTics();
        }
    }

    private void toggleFullSize() {
        if (null == plotLocation) {
            plotLocation = new PlotLocation(getLayoutX(), getLayoutY(), getPrefWidth(), getPrefHeight());
            setLayoutX(gridCellDim);
            setPrefWidth(((Pane) plotWallPane).getWidth() - 2 * gridCellDim);
            setLayoutY(gridCellDim + plotWallPane.getToolBarCount() * plotWallPane.getToolBarHeight());
            setPrefHeight(((Pane) plotWallPane).getHeight() - 2 * gridCellDim - plotWallPane.getToolBarCount() * plotWallPane.getToolBarHeight());
        } else {
            setLayoutX(plotLocation.x());
            setPrefWidth(plotLocation.w());
            setLayoutY(plotLocation.y());
            setPrefHeight(plotLocation.h());
            plotLocation = null;
        }
        updatePlot();
    }

    public void snapToGrid() {
        setLayoutX(getLayoutX() - (getLayoutX() % gridCellDim));
        setPrefWidth(getPrefWidth() - (getPrefWidth() % gridCellDim));
        setLayoutY(getLayoutY() - (getLayoutY() % gridCellDim));
        setPrefHeight(getPrefHeight() - (getPrefHeight() % gridCellDim));

        updatePlot();
    }

    private void initializePlotPane() {
        addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedEventHandler);
        addEventFilter(MouseEvent.MOUSE_PRESSED, mouseClickedEventHandler);
        addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedEventHandler);
        addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedEventHandler);
    }

    public void addPlot(AbstractPlot plot) {
        this.plot = plot;

        Pane plotPane = new Pane();
        plotPane.getChildren().add(plot);
        setCenter(plotPane);

        boolean isBlockCyclesPlot = (plot instanceof AnalysisBlockCyclesPlot);
        plotToolBarHeight = isBlockCyclesPlot ? 30 : 0;
        if (isBlockCyclesPlot) {
            Font toolBarFont = Font.font("SansSerif", FontWeight.BOLD, 10);

            ToolBar plotToolBar = new ToolBar();
            plotToolBar.setMinHeight(plotToolBarHeight);
            plotToolBar.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));
            plotToolBar.setStyle(plotToolBar.getStyle() + ";-fx-background-color:WHITE");

            Button replotButton = new Button("Replot");
            replotButton.setFont(toolBarFont);
            replotButton.setOnAction(event -> replot());
            plotToolBar.getItems().add(replotButton);

            Button resetDataButton = new Button("Reset Data");
            resetDataButton.setFont(toolBarFont);
            resetDataButton.setOnAction(event -> resetData());
            plotToolBar.getItems().add(resetDataButton);

            Button chauvenetButton = new Button("Chauvenet");
            chauvenetButton.setFont(toolBarFont);
            plotToolBar.getItems().add(chauvenetButton);

            Button toggleStatsButton = new Button("Toggle Stats");
            toggleStatsButton.setFont(toolBarFont);
            toggleStatsButton.setOnAction(event -> toggleShowStats());
            plotToolBar.getItems().add(toggleStatsButton);

            cycleCB = new CheckBox("Cycle");
            plotToolBar.getItems().add(cycleCB);
            cycleCB.selectedProperty().addListener(cycleCheckBoxChangeListener);

            Button synchButton = new Button("SYNCH");
            synchButton.setFont(toolBarFont);
            synchButton.setOnAction(event -> synch());
            plotToolBar.getItems().add(synchButton);


            setBottom(plotToolBar);
        }

        plot.widthProperty().bind(widthProperty());
        widthProperty().addListener((observable, oldValue, newValue) -> {
            plot.setWidthF(newValue.doubleValue());
            plot.updatePlotSize();
            plot.repaint();
        });

        plot.heightProperty().bind(heightProperty().subtract(plotToolBarHeight));
        heightProperty().addListener((observable, oldValue, newValue) -> {
            plot.setHeightF(newValue.doubleValue() - plotToolBarHeight);
            plot.updatePlotSize();
            plot.repaint();
        });

        plot.preparePanel(true, true);
        plot.repaint();
    }

    public void changeDataColor(AbstractPlot plot) {
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setLayoutX(getPrefWidth() / 2.0 - 50.0);
        colorPicker.setLayoutY(10.0);

        colorPicker.setValue(plot.getDataColor().color());
        getChildren().add(colorPicker);
        colorPicker.setVisible(true);

        colorPicker.setOnAction(t -> {
            plot.setDataColor(TripoliColor.create(colorPicker.getValue()));
            plot.repaint();
            getChildren().remove(colorPicker);
        });
    }

    public void toggleShowStats() {
        if (plot != null) {
            plot.toggleShowStats();
            plot.repaint();
        }
    }

    public void toggleRatiosLogRatios() {
        if (plot != null && (plot instanceof RatioHistogramPlot)) {
            ((RatioHistogramPlot) plot).toggleRatiosLogRatios();
        }
        if (plot != null && (plot instanceof AnalysisRatioPlot)) {
            ((AnalysisRatioPlot) plot).toggleRatiosLogRatios();
        }

    }

    public void replot() {
        if (plot != null) {
            plot.refreshPanel(true, true);
        }
    }

    public void resetData() {
        if (plot != null && (plot instanceof AnalysisBlockCyclesPlot)) {
            ((AnalysisBlockCyclesPlot) plot).resetData();
            plot.refreshPanel(true, true);
        }
    }

    public void synch() {
        for (Integer blockID : ((AnalysisBlockCyclesPlot) plot).getMapBlockIdToBlockCyclesRecord().keySet()) {
            boolean blockIncluded = ((AnalysisBlockCyclesPlot) plot).getMapBlockIdToBlockCyclesRecord().get(blockID).blockIncluded();
            for (Node node : ((PlotWallPane) getParent()).getChildren()) {
                if ((node instanceof TripoliPlotPane) && (node != this)) {
                    AnalysisBlockCyclesPlot plot = ((AnalysisBlockCyclesPlot) ((TripoliPlotPane) node).getPlot());
                    plot.getMapBlockIdToBlockCyclesRecord().put(
                            blockID,
                            plot.getMapBlockIdToBlockCyclesRecord().get(blockID).changeBlockIncluded(blockIncluded));
                    plot.getAnalysisBlockCyclesRecord().mapOfBlockIdToProcessStatus().put(blockID, RUN);
                    plot.repaint();
                }
            }
        }
    }

    public void updateSpeciesPlotted(
            boolean[] speciesChecked, boolean showFaradays, boolean showPMs, boolean showModels,
            boolean showUncertainties, TripoliConstants.IntensityUnits intensityUnits, boolean baselineCorr, boolean gainCorr, boolean logScale, boolean reScaleX, boolean reScaleY) {
        if (plot != null && (plot instanceof SpeciesIntensityAnalysisPlot)) {
            ((SpeciesIntensityAnalysisPlot) plot).setSpeciesChecked(speciesChecked);
            ((SpeciesIntensityAnalysisPlot) plot).setShowFaradays(showFaradays);
            ((SpeciesIntensityAnalysisPlot) plot).setShowPMs(showPMs);
            ((SpeciesIntensityAnalysisPlot) plot).setShowModels(showModels);
            ((SpeciesIntensityAnalysisPlot) plot).setIntensityUnits(intensityUnits);
            ((SpeciesIntensityAnalysisPlot) plot).setBaselineCorr(baselineCorr);
            ((SpeciesIntensityAnalysisPlot) plot).setGainCorr(gainCorr);
            ((SpeciesIntensityAnalysisPlot) plot).setLogScale(logScale);
            ((SpeciesIntensityAnalysisPlot) plot).setShowUncertainties(showUncertainties);
            plot.refreshPanel(reScaleX, reScaleY);
        }
    }

    public void updateAnalysisRatiosPlotted(boolean blockMode, boolean logScale, boolean reScaleX, boolean reScaleY) {
        if (plot != null && (plot instanceof AnalysisBlockCyclesPlot)) {
            ((AnalysisBlockCyclesPlot) plot).setBlockMode(blockMode);
            ((AnalysisBlockCyclesPlot) plot).setLogScale(logScale);

            cycleCB.selectedProperty().removeListener(cycleCheckBoxChangeListener);
            cycleCB.setSelected(!blockMode);
            cycleCB.selectedProperty().addListener(cycleCheckBoxChangeListener);

            plot.refreshPanel(reScaleX, reScaleY);
        }
    }

    public void resetAnalysisIntensityZoom(boolean[] zoomFlagsXY) {
        ((SpeciesIntensityAnalysisPlot) plot).setZoomFlagsXY(zoomFlagsXY);
    }

    public void resetAnalysisRatioZoom(boolean[] zoomFlagsXY) {
        ((AnalysisBlockCyclesPlot) plot).setZoomFlagsXY(zoomFlagsXY);
    }

    private record PlotLocation(
            double x,
            double y,
            double w,
            double h
    ) {
    }

    private class CheckBoxChangeListener implements ChangeListener<Boolean> {
        private final CheckBox checkBox;

        public CheckBoxChangeListener(CheckBox checkBox) {
            this.checkBox = checkBox;
        }

        /**
         * @param observable The {@code ObservableValue} which value changed
         * @param oldValue   The old value
         * @param newValue   The new value
         */
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            updateAnalysisRatiosPlotted(!newValue, false, false, true);
        }
    }
}