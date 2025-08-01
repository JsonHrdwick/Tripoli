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

package org.cirdles.tripoli.gui.dataViews.plots.plotsControllers.ogTripoliPlots.analysisPlots;

import com.google.common.base.Strings;
import com.google.common.primitives.Booleans;
import javafx.event.EventHandler;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.cirdles.tripoli.constants.TripoliConstants;
import org.cirdles.tripoli.expressions.userFunctions.UserFunction;
import org.cirdles.tripoli.gui.dataViews.plots.*;
import org.cirdles.tripoli.plots.analysisPlotBuilders.AnalysisBlockCyclesRecord;
import org.cirdles.tripoli.plots.compoundPlotBuilders.PlotBlockCyclesRecord;
import org.cirdles.tripoli.sessions.analysis.AnalysisInterface;
import org.cirdles.tripoli.sessions.analysis.AnalysisStatsRecord;
import org.cirdles.tripoli.sessions.analysis.BlockStatsRecord;
import org.cirdles.tripoli.sessions.analysis.GeometricMeanStatsRecord;
import org.cirdles.tripoli.utilities.mathUtilities.FormatterForSigFigN;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.StrictMath.*;
import static java.util.Arrays.binarySearch;
import static java.util.Map.entry;
import static org.cirdles.tripoli.sessions.analysis.GeometricMeanStatsRecord.generateGeometricMeanStats;
import static org.cirdles.tripoli.utilities.mathUtilities.FormatterForSigFigN.countOfTrailingDigitsForSigFig;
import static org.cirdles.tripoli.utilities.mathUtilities.MathUtilities.applyChauvenetsCriterion;

/**
 * @author James F. Bowring
 */
public class AnalysisBlockCyclesPlotOG extends AbstractPlot implements AnalysisBlockCyclesPlotI {
    private final Tooltip tooltip;
    private final String tooltipTextSculpt = "Left mouse: cntrl click toggles block, Dbl-click to Sculpt data. Right mouse: cntrl click zooms one block, Dbl-click toggles full view.";
    private final String tooltipTextExitSculpt = "Left mouse: cntrl click toggles block, Dbl-click Exits Sculpting. Right mouse: cntrl click zooms one block, Dbl-click toggles full view.";
    int[] blockIDsPerTimeSlot;
    private AnalysisInterface analysis;
    private Map<Integer, PlotBlockCyclesRecord> mapBlockIdToBlockCyclesRecord;
    private UserFunction userFunction;

    private double[] oneSigmaForCycles;
    private boolean logScale;
    private boolean[] zoomFlagsXY;
    private PlotWallPaneInterface parentWallPane;
    private boolean isRatio;
    private boolean blockMode;
    private AnalysisStatsRecord analysisStatsRecord;
    private double selectorBoxX;
    private double selectorBoxY;
    private boolean inSculptorMode;
    private int sculptBlockID;
    private boolean showSelectionBox;
    private int countOfPreviousBlockIncludedData;
    private boolean inZoomBoxMode;
    private boolean showZoomBox;
    private double zoomBoxX;
    private double zoomBoxY;
    private boolean ignoreRejects;

    private AnalysisBlockCyclesPlotOG(
            AnalysisInterface analysis,
            Rectangle bounds,
            UserFunction userFunction,
            int[] blockIDsPerTimeSlot,
            PlotWallPane parentWallPane) {
        super(bounds,
                185, 25,
                new String[]{userFunction.getName()
                        + "  " + "x\u0304" + "= 0" //+ String.format("%8.8g", analysisBlockCyclesRecord.analysisMean()).trim()
                        , "\u00B1" + " 0"},//String.format("%8.5g", analysisBlockCyclesRecord.analysisOneSigma()).trim()},
                "Blocks & Cycles by Time",
                userFunction.isTreatAsIsotopicRatio() ? "Ratio" : "Function");
        this.analysis = analysis;
        this.userFunction = userFunction;
        this.mapBlockIdToBlockCyclesRecord = userFunction.getMapBlockIdToBlockCyclesRecord();
        this.blockIDsPerTimeSlot = blockIDsPerTimeSlot;
        this.logScale = false;
        this.zoomFlagsXY = new boolean[]{true, true};
        this.parentWallPane = parentWallPane;
        this.blockMode = userFunction.getReductionMode().equals(TripoliConstants.ReductionModeEnum.BLOCK);
        this.isRatio = userFunction.isTreatAsIsotopicRatio();
        this.ignoreRejects = false;

        tooltip = new Tooltip(tooltipTextSculpt);
        Tooltip.install(this, tooltip);

        setOnMouseClicked(new MouseClickEventHandler());
    }

    public static AbstractPlot generatePlot(
            Rectangle bounds, AnalysisInterface analysis, UserFunction userFunction,
            int[] blockIDsPerTimeSlot, PlotWallPane parentWallPane) {
        return new AnalysisBlockCyclesPlotOG(analysis, bounds, userFunction, blockIDsPerTimeSlot, parentWallPane);
    }

    public PlotWallPaneInterface getParentWallPane() {
        return parentWallPane;
    }

    public void setLogScale(boolean logScale) {
        this.logScale = logScale;
    }

    /**
     * @return
     */
    @Override
    public boolean getBlockMode() {
        return blockMode;
    }

    public void setBlockMode(boolean blockMode) {
        this.blockMode = blockMode;
    }

    public void setZoomFlagsXY(boolean[] zoomFlagsXY) {
        this.zoomFlagsXY = zoomFlagsXY;
    }

    public void resetBlockMode() {
        blockMode = userFunction.isTreatAsIsotopicRatio();
    }

    @Override
    public void preparePanel(boolean reScaleX, boolean reScaleY) {
        selectorBoxX = mouseStartX;
        selectorBoxY = mouseStartY;
        zoomBoxX = mouseStartX;
        zoomBoxY = mouseStartY;

        // process blocks
        int cyclesPerBlock = mapBlockIdToBlockCyclesRecord.get(1).cyclesIncluded().length;

        if (reScaleX) {
            int xDataLength = 0;
            for (Map.Entry<Integer, PlotBlockCyclesRecord> entry : mapBlockIdToBlockCyclesRecord.entrySet()) {
                xDataLength += entry.getValue().cycleMeansData().length;
            }
            xAxisData = new double[xDataLength];
            for (int i = 0; i < xAxisData.length; i++) {
                xAxisData[i] = i + 1;
            }

            displayOffsetX = 0.0;
            inSculptorMode = false;
            sculptBlockID = 0;
            showSelectionBox = false;
            removeEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedEventHandler);
            setOnMouseDragged(new AnalysisBlockCyclesPlotOG.MouseDraggedEventHandler());
            setOnMousePressed(new AnalysisBlockCyclesPlotOG.MousePressedEventHandler());
            setOnMouseReleased(new AnalysisBlockCyclesPlotOG.MouseReleasedEventHandler());
            addEventFilter(ScrollEvent.SCROLL, scrollEventEventHandler);

            minX = 1.0;
            maxX = xAxisData.length;
        }

        yAxisData = new double[xAxisData.length];
        oneSigmaForCycles = new double[xAxisData.length];
        boolean doInvert = userFunction.isInverted() && userFunction.isTreatAsIsotopicRatio();
        for (Map.Entry<Integer, PlotBlockCyclesRecord> entry : mapBlockIdToBlockCyclesRecord.entrySet()) {
            PlotBlockCyclesRecord plotBlockCyclesRecord = entry.getValue();
            if (plotBlockCyclesRecord != null) {
                int availableCyclesPerBlock = plotBlockCyclesRecord.cycleMeansData().length;
                if (doInvert) {
                    System.arraycopy(plotBlockCyclesRecord.invertedCycleMeansData(), 0, yAxisData, (plotBlockCyclesRecord.blockID() - 1) * cyclesPerBlock, availableCyclesPerBlock);
                } else {
                    System.arraycopy(plotBlockCyclesRecord.cycleMeansData(), 0, yAxisData, (plotBlockCyclesRecord.blockID() - 1) * cyclesPerBlock, availableCyclesPerBlock);
                }
                System.arraycopy(plotBlockCyclesRecord.cycleOneSigmaData(), 0, oneSigmaForCycles, (plotBlockCyclesRecord.blockID() - 1) * cyclesPerBlock, availableCyclesPerBlock);
            }
        }

        plotAxisLabelY = userFunction.isTreatAsIsotopicRatio() ? "Ratio" : "Function";
        if (logScale && userFunction.isTreatAsIsotopicRatio()) {
            for (int i = 0; i < yAxisData.length; i++) {
                yAxisData[i] = (yAxisData[i] > 0.0) ? log(yAxisData[i]) : 0.0;
                // TODO: uncertainties for logs
                oneSigmaForCycles[i] = 0.0;
            }
            plotAxisLabelY = "Log Ratio";
        }

        if (reScaleY || ignoreRejects) {
            // calculate ratio and unct across all included blocks
            minY = Double.MAX_VALUE;
            maxY = -Double.MAX_VALUE;

            for (int i = 0; i < yAxisData.length; i++) {
                // TODO: handle logratio uncertainties
                int blockIndex = i / cyclesPerBlock;
                if ((yAxisData[i] != 0.0) && (!ignoreRejects || mapBlockIdToBlockCyclesRecord.get(blockIndex + 1).cyclesIncluded()[i % cyclesPerBlock])) {
                    minY = min(minY, yAxisData[i] - oneSigmaForCycles[i]);
                    maxY = max(maxY, yAxisData[i] + oneSigmaForCycles[i]);
                }
            }

            displayOffsetY = 0.0;
        }
        prepareExtents(reScaleX, reScaleY);
        showXaxis = false;
        showStats = true;
        calcStats();
        calculateTics();
        repaint();
    }

    /**
     * @param g2d
     */
    @Override
    public void labelAxisY(GraphicsContext g2d) {
        // do nothing
    }

    @Override
    public void calculateTics() {
        super.calculateTics();
        zoomChunkX = zoomFlagsXY[0] ? zoomChunkX : 0.0;
        zoomChunkY = zoomFlagsXY[1] ? zoomChunkY : 0.0;
    }

    public void calcStats() {
        analysisStatsRecord = userFunction.calculateAnalysisStatsRecord(analysis);
    }

    /**
     * For Block Mode:
     * Generates the values for the lesserSigmaPct, plusSigmaPct, and the minusSigmaPct.
     * Also returns the generated value for countOfTrailingDigitsForSigFig
     * @param geoWeightedMeanRatio
     * @return
     */
    HashMap<String, Double> calcSigmaPctsBM(double geoWeightedMeanRatio) {
        int countOfTrailingDigitsForSigFig;

        double geoWeightedMeanRatioPlusOneSigma = exp(analysisStatsRecord.blockModeWeightedMean() + analysisStatsRecord.blockModeWeightedMeanOneSigma());
        double geoWeightedMeanRatioMinusOneSigma = exp(analysisStatsRecord.blockModeWeightedMean() - analysisStatsRecord.blockModeWeightedMeanOneSigma());
        double geoWeightedMeanRatioPlusOneSigmaPct = (geoWeightedMeanRatioPlusOneSigma - geoWeightedMeanRatio) / geoWeightedMeanRatio * 100.0;
        double geoWeightedMeanRatioMinusOneSigmaPct = (geoWeightedMeanRatio - geoWeightedMeanRatioMinusOneSigma) / geoWeightedMeanRatio * 100.0;

        countOfTrailingDigitsForSigFig = countOfTrailingDigitsForSigFig(Math.min(geoWeightedMeanRatioPlusOneSigmaPct, geoWeightedMeanRatioMinusOneSigmaPct), 2);
        double plusSigmaPct = (new BigDecimal(geoWeightedMeanRatioPlusOneSigmaPct).setScale(countOfTrailingDigitsForSigFig, RoundingMode.HALF_UP)).doubleValue();
        double minusSigmaPct = (new BigDecimal(geoWeightedMeanRatioMinusOneSigmaPct).setScale(countOfTrailingDigitsForSigFig, RoundingMode.HALF_UP)).doubleValue();

        double lesserSigmaPct = Math.min(plusSigmaPct, minusSigmaPct);

        HashMap<String, Double> output = new HashMap<>();
        output.put("lesserSigmaPct", lesserSigmaPct);
        output.put("plusSigmaPct", plusSigmaPct);
        output.put("minusSigmaPct", minusSigmaPct);
        output.put("countOfTrailingDigitsForSigFig", (double) countOfTrailingDigitsForSigFig);

        return output;
    }

    /**
     * For Cycle Mode:
     * Generates the necessary values for Cycle Mode
     * @param geometricMeanStatsRecord
     * @param geoMean
     * @return HashMap containing the values of plusErrPct, minusErrPct, plusSigmaPct, minusSigmaPct, geoMeanPlusOneStandardDeviation, countOfTrailingDigitsForStdErrPct, and countOfTralingDigitsForOneSigmaPct
     */
    HashMap<String, Double> calcSigmaPctsCM(GeometricMeanStatsRecord geometricMeanStatsRecord, double geoMean) {
        double geoMeanPlusOneStandardError = geometricMeanStatsRecord.geoMeanPlusOneStdErr();
        double geoMeanMinusOneStandardError = geometricMeanStatsRecord.geoMeanMinusOneStdErr();
        double geoMeanRatioPlusOneStdErrPct = (geoMeanPlusOneStandardError - geoMean) / geoMean * 100.0;
        double geoMeanRatioMinusOneStdErrPct = (geoMean - geoMeanMinusOneStandardError) / geoMean * 100.0;

        double smallerGeoMeanRatioOneStdErrPct = Math.min(geoMeanRatioPlusOneStdErrPct, geoMeanRatioMinusOneStdErrPct);
        int countOfTrailingDigitsForStdErrPct = countOfTrailingDigitsForSigFig(smallerGeoMeanRatioOneStdErrPct, 2);
        double plusErrPct = (new BigDecimal(geoMeanRatioPlusOneStdErrPct).setScale(countOfTrailingDigitsForStdErrPct, RoundingMode.HALF_UP)).doubleValue();
        double minusErrPct = (new BigDecimal(geoMeanRatioMinusOneStdErrPct).setScale(countOfTrailingDigitsForStdErrPct, RoundingMode.HALF_UP)).doubleValue();

        double geoMeanPlusOneStandardDeviation = geometricMeanStatsRecord.geoMeanPlusOneStdDev();
        double geoMeanMinusOneStandardDeviation = geometricMeanStatsRecord.geoMeanMinusOneStdDev();
        double geoMeanRatioPlusOneSigmaPct = (geoMeanPlusOneStandardDeviation - geoMean) / geoMean * 100.0;
        double geoMeanRatioMinusOneSigmaPct = (geoMean - geoMeanMinusOneStandardDeviation) / geoMean * 100.0;
        double smallerGeoMeanRatioForOneSigmaPct = Math.min(geoMeanRatioPlusOneSigmaPct, geoMeanRatioMinusOneSigmaPct);
        int countOfTrailingDigitsForOneSigmaPct = countOfTrailingDigitsForSigFig(smallerGeoMeanRatioForOneSigmaPct, 2);
        double plusSigmaPct = (new BigDecimal(geoMeanRatioPlusOneSigmaPct).setScale(countOfTrailingDigitsForOneSigmaPct, RoundingMode.HALF_UP)).doubleValue();
        double minusSigmaPct = (new BigDecimal(geoMeanRatioMinusOneSigmaPct).setScale(countOfTrailingDigitsForOneSigmaPct, RoundingMode.HALF_UP)).doubleValue();

        HashMap<String, Double> output = new HashMap<>();
        output.put("plusErrPct", plusErrPct);
        output.put("minusErrPct", minusErrPct);
        output.put("plusSigmaPct", plusSigmaPct);
        output.put("minusSigmaPct", minusSigmaPct);
        output.put("geoMeanPlusOneStandardDeviation", geoMeanPlusOneStandardDeviation);
        output.put("countOfTrailingDigitsForStdErrPct", (double) countOfTrailingDigitsForStdErrPct);
        output.put("countOfTrailingDigitsForOneSigmaPct", (double) countOfTrailingDigitsForOneSigmaPct);

        return output;
    }

    /**
     * Returns the FormattedStats object needed for the meaAsString(), unctAsString(), and stdvAsString() methods.
     *
     * @param mean
     * @param stdErr
     * @return
     */
    FormatterForSigFigN.FormattedStats calcFormattedStats(double mean, double stdErr, double stdDev) {

        FormatterForSigFigN.FormattedStats formattedStats;
        if ((abs(mean) >= 1e7) || (abs(mean) <= 1e-5)) {
            formattedStats =
                    FormatterForSigFigN.formatToScientific(mean, stdErr, stdDev, 2).padLeft();
        } else {
            formattedStats =
                    FormatterForSigFigN.formatToSigFig(mean, stdErr, stdDev, 2).padLeft();
        }

        return formattedStats;


    }

    /**
     * @param g2d
     */
    @Override
    public void showLegend(GraphicsContext g2d) {
        int textLeft = 5;
        int textTop = 18;
        int textDeltaY = 18;

        Font normalFourteen = Font.font("Courier New", FontWeight.BOLD, 16);
        Font monospacedEight = Font.font("Monospaced", FontWeight.NORMAL, 8);

        g2d.setFill(Paint.valueOf("RED"));
        g2d.setFont(Font.font("SansSerif", 16));
        String title = userFunction.showCorrectName();
        if (userFunction.isTreatAsCustomExpression()){
            title = userFunction.getCustomExpression().getName();
        }
        if (isRatio && logScale) {
            title = "LogRatio " + title;
        }
        if (isRatio && !logScale) {
            title = "Ratio " + title;
        }
        g2d.fillText(title, textLeft, textTop);

        if (inSculptorMode) {
            g2d.fillText("  >> SCULPT MODE <<", textLeft + 200, textTop);
        }

        g2d.setFill(Paint.valueOf("BLACK"));

        if (isRatio && !logScale) {
            g2d.setFont(normalFourteen);
            String twoSigString;
            int countOfTrailingDigitsForSigFig;

            if (blockMode) {
                g2d.fillText("Block Mode:", textLeft + 5, textTop += textDeltaY);
                double geoWeightedMeanRatio = exp(analysisStatsRecord.blockModeWeightedMean());

                if (!Double.isNaN(geoWeightedMeanRatio)) {
                    HashMap<String, Double> sigmaPcts = calcSigmaPctsBM(geoWeightedMeanRatio);
                    double lesserSigmaPct = sigmaPcts.get("lesserSigmaPct");
                    double plusSigmaPct = sigmaPcts.get("plusSigmaPct");
                    double minusSigmaPct = sigmaPcts.get("minusSigmaPct");

                    countOfTrailingDigitsForSigFig = sigmaPcts.get("countOfTrailingDigitsForSigFig").intValue();

                    String meanAsString = calcFormattedStats(geoWeightedMeanRatio, lesserSigmaPct, 0).meanAsString();

                    g2d.fillText("x  = " + meanAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("\u0304", textLeft + 10, textTop);

                    boolean meanIsPlottable = (mapY(geoWeightedMeanRatio) >= topMargin) && (mapY(geoWeightedMeanRatio) <= topMargin + plotHeight);
                    if (meanIsPlottable) {
//                        g2d.setStroke(OGTRIPOLI_MEAN);
                        g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
                        g2d.strokeLine(Math.max(mapX(xAxisData[0]), leftMargin) - 2, mapY(geoWeightedMeanRatio), Math.min(mapX(xAxisData[xAxisData.length - 1]), leftMargin + plotWidth) + 2, mapY(geoWeightedMeanRatio));
                        g2d.setStroke(Paint.valueOf("BLACK"));
                    }

                    String sigmaPctString;
                    String sigmaMinusPctString = "";
                    if (plusSigmaPct == minusSigmaPct) {
                        sigmaPctString = " " + plusSigmaPct;
                    } else {
                        sigmaPctString = "+" + plusSigmaPct;
                        sigmaMinusPctString = "" + minusSigmaPct;
                    }

                    sigmaPctString = appendTrailingZeroIfNeeded(sigmaPctString, countOfTrailingDigitsForSigFig);
                    sigmaMinusPctString = appendTrailingZeroIfNeeded(sigmaMinusPctString, countOfTrailingDigitsForSigFig);

                    g2d.fillText("%\u03C3  =" + sigmaPctString, textLeft + 0, textTop += textDeltaY);
                    g2d.fillText("x", textLeft + 20, textTop + 6);
                    g2d.fillText("\u0304", textLeft + 20, textTop + 6);
                    if (sigmaMinusPctString.length() > 0) {
                        g2d.fillText("     " + sigmaMinusPctString, textLeft + 0, textTop += textDeltaY);
                    }

                    double chiSquared = analysisStatsRecord.blockModeChiSquared();
                    if (Double.isNaN(chiSquared)) {
                        twoSigString = "NaN";
                    } else if (Double.isInfinite(chiSquared)) {
                        twoSigString = "Infinite";
                    } else {
                        twoSigString = ((chiSquared >= 10) ? "" : " ") + (new BigDecimal(chiSquared).setScale(countOfTrailingDigitsForSigFig, RoundingMode.HALF_UP)).toPlainString();
                    }
                    g2d.fillText("\u03C7  =" + twoSigString, textLeft + 10, textTop += textDeltaY);
                    g2d.setFont(monospacedEight);
                    g2d.fillText("red", textLeft + 20, textTop + 6);
                    g2d.fillText("2", textLeft + 19, textTop - 7);
                    g2d.setFont(normalFourteen);

                    int countIncluded = analysisStatsRecord.countOfIncludedBlocks();
                    g2d.fillText("n  = " + countIncluded + "/" + analysisStatsRecord.blockStatsRecords().length, textLeft + 10, textTop += textDeltaY);

                } else {
                    g2d.fillText("Bad Data", textLeft + 5, textTop += textDeltaY);
                }
            } else { // cycle mode of ratio
                g2d.setFont(normalFourteen);
                g2d.fillText("Cycle Mode:", textLeft + 5, textTop += textDeltaY);

                /*
                Round the (1-sigma percent) standard error and (1-sigma percent) standard deviation to two significant decimal places.
                 If there is a (+ and -) display on either because they are different, round both to the number of decimal places
                 belonging to the smallest increment.  So, below, 0.85 gets rounded to the hundredths to get two significant figures.,
                 For the Percent standard deviation, +10.0 and -9.1 get rounded to the tenths decimal place, matching the smallest
                 increment (tenths vs. the ones places for the +10).
                 Use two significant figures of the 1-sigma absolute standard error to determine where to round the mean.
                 */

                GeometricMeanStatsRecord geometricMeanStatsRecord =
                        generateGeometricMeanStats(analysisStatsRecord.cycleModeMean(), analysisStatsRecord.cycleModeStandardDeviation(), analysisStatsRecord.cycleModeStandardError());
                double geoMean = geometricMeanStatsRecord.geoMean();
                if (!Double.isNaN(geoMean)) {

                    HashMap<String, Double> keyValues = calcSigmaPctsCM(geometricMeanStatsRecord, geoMean);
                    double plusErrPct = keyValues.get("plusErrPct");
                    double minusErrPct = keyValues.get("minusErrPct");
                    double plusSigmaPct = keyValues.get("plusSigmaPct");
                    double minusSigmaPct = keyValues.get("minusSigmaPct");
                    double geoMeanPlusOneStandardDeviation = keyValues.get("geoMeanPlusOneStandardDeviation");
                    int countOfTrailingDigitsForStdErrPct = keyValues.get("countOfTrailingDigitsForStdErrPct").intValue();
                    int countOfTrailingDigitsForOneSigmaPct = keyValues.get("countOfTrailingDigitsForOneSigmaPct").intValue();

                    String meanAsString;
                    String errPctString;
                    String errMinusPctString = "";
                    String sigmaPctString;
                    String sigmaMinusPctString = "";

                    meanAsString = calcFormattedStats(geoMean, geoMeanPlusOneStandardDeviation - geoMean, 0).meanAsString();

                    g2d.fillText("x  = " + meanAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("\u0304", textLeft + 10, textTop);
                    boolean meanIsPlottable = (mapY(geoMean) >= topMargin) && (mapY(geoMean) <= topMargin + plotHeight);
                    if (meanIsPlottable) {
//                        g2d.setStroke(OGTRIPOLI_MEAN);
                        g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
                        g2d.strokeLine(Math.max(mapX(xAxisData[0]), leftMargin) - 2, mapY(geoMean), Math.min(mapX(xAxisData[xAxisData.length - 1]), leftMargin + plotWidth) + 2, mapY(geoMean));
                        g2d.setStroke(Paint.valueOf("BLACK"));
                    }

                    if (plusErrPct == minusErrPct) {
                        errPctString = " " + plusErrPct;
                    } else {
                        errPctString = "+" + plusErrPct;
                        errMinusPctString = "-" + minusErrPct;
                    }
                    errPctString = appendTrailingZeroIfNeeded(errPctString, countOfTrailingDigitsForStdErrPct);
                    errMinusPctString = appendTrailingZeroIfNeeded(errMinusPctString, countOfTrailingDigitsForStdErrPct);

                    g2d.fillText("%\u03C3  =" + errPctString, textLeft + 0, textTop += textDeltaY);
                    g2d.fillText("x", textLeft + 20, textTop + 6);
                    g2d.fillText("\u0304", textLeft + 20, textTop + 6);
                    if (errMinusPctString.length() > 0) {
                        g2d.fillText("     " + errMinusPctString, textLeft + 0, textTop += textDeltaY);
                    }

                    if (plusSigmaPct == minusSigmaPct) {
                        sigmaPctString = " " + plusSigmaPct;
                    } else {
                        sigmaPctString = "+" + plusSigmaPct;
                        sigmaMinusPctString = "-" + minusSigmaPct;
                    }

                    sigmaPctString = appendTrailingZeroIfNeeded(sigmaPctString, countOfTrailingDigitsForOneSigmaPct);
                    sigmaMinusPctString = appendTrailingZeroIfNeeded(sigmaMinusPctString, countOfTrailingDigitsForOneSigmaPct);

                    g2d.fillText("%\u03C3  =" + sigmaPctString, textLeft + 0, textTop += textDeltaY);
                    if (sigmaMinusPctString.length() > 0) {
                        g2d.fillText("     " + sigmaMinusPctString, textLeft + 0, textTop += textDeltaY);
                    }

                    int countOfIncludedCycles = analysisStatsRecord.countOfIncludedCycles();
                    int countOfTotalCycles = analysisStatsRecord.countOfTotalCycles();
                    g2d.fillText("n  = " + countOfIncludedCycles + "/" + countOfTotalCycles, textLeft + 10, textTop += textDeltaY);

                } else {
                    g2d.fillText("Bad Data", textLeft + 5, textTop += 2 * textDeltaY);
                }
            }
        } else { // handle logratio or function
            g2d.setFont(normalFourteen);
            String meanSigned;
            String twoSigString;
            int countOfTrailingDigitsForSigFig;

            if (blockMode) {
                g2d.fillText("Block Mode:", textLeft + 5, textTop += textDeltaY);

                double weightedMeanOneSigma = analysisStatsRecord.blockModeWeightedMeanOneSigma();
                countOfTrailingDigitsForSigFig = countOfTrailingDigitsForSigFig(weightedMeanOneSigma, 2);

                double weightedMean = analysisStatsRecord.blockModeWeightedMean();
                if (!Double.isNaN(weightedMean)) {
                    String meanAsString;
                    String unctAsString;

                    if ((abs(weightedMean) >= 1e7) || (abs(weightedMean) <= 1e-5)) {
                        FormatterForSigFigN.FormattedStats formattedStats =
                                FormatterForSigFigN.formatToScientific(weightedMean, weightedMeanOneSigma, 0, 2).padLeft();
                        meanAsString = formattedStats.meanAsString();
                        unctAsString = formattedStats.unctAsString();
                    } else {
                        FormatterForSigFigN.FormattedStats formattedStats =
                                FormatterForSigFigN.formatToSigFig(weightedMean, weightedMeanOneSigma, 0, 2).padLeft();
                        meanAsString = formattedStats.meanAsString();
                        unctAsString = formattedStats.unctAsString();
                    }

                    g2d.fillText("x  = " + meanAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("\u0304", textLeft + 10, textTop);
                    g2d.fillText("\u03C3  = " + unctAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("x", textLeft + 18, textTop + 6);
                    g2d.fillText("\u0304", textLeft + 18, textTop + 6);

                    double chiSquared = analysisStatsRecord.blockModeChiSquared();
                    meanSigned = (weightedMean < 0) ? " " : "";
                    if (!Double.isNaN(chiSquared) && !Double.isInfinite(chiSquared)) {
                        twoSigString = meanSigned + ((chiSquared >= 10.0) ? "" : " ") + (new BigDecimal(chiSquared).setScale(countOfTrailingDigitsForSigFig, RoundingMode.HALF_UP)).toPlainString();
                        if (countOfTrailingDigitsForSigFig == 0) {
                            twoSigString = Strings.padStart(twoSigString.trim(), unctAsString.length() + 1, ' ');
                        } else {
                            twoSigString = appendTrailingZeroIfNeeded(twoSigString, countOfTrailingDigitsForSigFig);
                        }
                        g2d.fillText("\u03C7  =" + twoSigString, textLeft + 10, textTop += textDeltaY);
                        g2d.setFont(monospacedEight);
                        g2d.fillText("red", textLeft + 20, textTop + 6);
                        g2d.fillText("2", textLeft + 19, textTop - 7);
                        g2d.setFont(normalFourteen);
                    }

                    int countIncluded = analysisStatsRecord.countOfIncludedBlocks();
                    g2d.fillText("n  = " + countIncluded + "/" + analysisStatsRecord.blockStatsRecords().length, textLeft + 10, textTop += textDeltaY);

                    boolean meanIsPlottable = (mapY(weightedMean) >= topMargin) && (mapY(weightedMean) <= topMargin + plotHeight);
                    if (meanIsPlottable) {
//                        g2d.setStroke(OGTRIPOLI_MEAN);
                        g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
                        g2d.strokeLine(Math.max(mapX(xAxisData[0]), leftMargin) - 2, mapY(weightedMean), Math.min(mapX(xAxisData[xAxisData.length - 1]), leftMargin + plotWidth) + 2, mapY(weightedMean));
                        g2d.setStroke(Paint.valueOf("BLACK"));
                    }
                    double plottedOneSigmaHeight = Math.min(mapY(weightedMean - weightedMeanOneSigma), topMargin + plotHeight) - Math.max(mapY(weightedMean + weightedMeanOneSigma), topMargin);
//                    g2d.setFill(OGTRIPOLI_ONESIGMA_SEMI);
                    g2d.setFill(Color.web(analysis.getOneSigmaHexColorString(),.25)); // TODO: set this to a constant or find some other way to deliver opacity
                    g2d.fillRect(Math.max(mapX(xAxisData[0]), leftMargin),
                            Math.max(mapY(weightedMean + weightedMeanOneSigma), topMargin),
                            Math.min(mapX(xAxisData[xAxisData.length - 1]), leftMargin + plotWidth) - Math.max(mapX(xAxisData[0]), leftMargin),
                            plottedOneSigmaHeight);
                    g2d.setFill(Paint.valueOf("BLACK"));

                } else {
                    g2d.fillText("Bad Data", textLeft + 5, textTop += 2 * textDeltaY);
                }

            } else { // cycle mode of logratio or function
                /*
                Round the (1-sigma absolute) standard error to two significant decimal places (e.g., 0.0085 below).
                Round the mean and the standard deviation to the same number of decimal places.
                 */
                g2d.setFont(normalFourteen);
                g2d.fillText("Cycle Mode:", textLeft + 5, textTop += textDeltaY);

                double cycleModeMean = analysisStatsRecord.cycleModeMean();
                double cycleModeStandardError = analysisStatsRecord.cycleModeStandardError();
                double cycleModeStandardDeviation = analysisStatsRecord.cycleModeStandardDeviation();

                if (!Double.isNaN(cycleModeMean)) {
                    String meanAsString;
                    String unctAsString;
                    String stdvAsString;

                    FormatterForSigFigN.FormattedStats formattedStats = calcFormattedStats(cycleModeMean, cycleModeStandardError, cycleModeStandardDeviation);

                    meanAsString = formattedStats.meanAsString();
                    unctAsString = formattedStats.unctAsString();
                    stdvAsString = formattedStats.stdvAsString();

                    g2d.fillText("x  = " + meanAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("\u0304", textLeft + 10, textTop);
                    g2d.fillText("\u03C3  = " + unctAsString, textLeft + 10, textTop += textDeltaY);
                    g2d.fillText("x", textLeft + 18, textTop + 6);
                    g2d.fillText("\u0304", textLeft + 18, textTop + 6);
                    g2d.fillText("\u03C3  = " + stdvAsString, textLeft + 10, textTop += textDeltaY);

                    int countOfIncludedCycles = analysisStatsRecord.countOfIncludedCycles();
                    int countOfTotalCycles = analysisStatsRecord.countOfTotalCycles();
                    g2d.fillText("n  = " + countOfIncludedCycles + "/" + countOfTotalCycles, textLeft + 10, textTop += textDeltaY);

                } else {
                    g2d.fillText("Bad Data", textLeft + 5, textTop += 2 * textDeltaY);
                }
            }
        }

        // legend in colors
        // modified per issue #263
        g2d.setFont(normalFourteen);
//        g2d.fillText("Legend:", textLeft + 5, textTop += textDeltaY * 2);
        //  TODO: Change the legend colors based on user selection

        g2d.setFill(Color.web(analysis.getTwoSigmaHexColorString()));
        g2d.fillRect(textLeft + 8, textTop + textDeltaY, 27, 50);
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.fillText("2\u03C3", textLeft + 11, textTop + 2 * textDeltaY);
//        g2d.fillText("x", textLeft + 26, textTop + 2 * textDeltaY + 7);
//        g2d.fillText("\u0304", textLeft + 26, textTop + 2 * textDeltaY + 7);


        g2d.setFill(Color.web(analysis.getOneSigmaHexColorString()));
        g2d.fillRect(textLeft + 35, textTop + textDeltaY + 25, 25, 25);
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.fillText("\u03C3", textLeft + 42, textTop + 3.2 * textDeltaY);


        g2d.setFill(Color.web(analysis.getTwoStandardErrorHexColorString()));
        g2d.fillRect(textLeft + 60, textTop + textDeltaY + 25, 25, 25);
        g2d.setFill(Paint.valueOf("BLACK"));
        g2d.fillText("\u03C3", textLeft + 62, textTop + 3.2 * textDeltaY);
        g2d.fillText("x", textLeft + 72, textTop + 3 * textDeltaY + 9);
        g2d.fillText("\u0304", textLeft + 72, textTop + 3 * textDeltaY + 9);

        g2d.setFont(normalFourteen);

        g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
        g2d.setLineWidth(1.5);
        g2d.strokeLine(textLeft + 5, textTop + textDeltaY + 50, textLeft + 90, textTop + textDeltaY + 50);
        g2d.fillText("x", textLeft + 95, textTop + 3.2 * textDeltaY + 14);
        g2d.fillText("\u0304", textLeft + 95, textTop + 3.2 * textDeltaY + 14);


    }

    private String appendTrailingZeroIfNeeded(String valueString, int countOfTrailingDigits) {
        String retVal = valueString;
        if (!valueString.isBlank()) {
            String checkForTrailingZero = String.format("%,1." + countOfTrailingDigits + "f", Double.parseDouble(valueString));
            if (checkForTrailingZero.substring(checkForTrailingZero.length() - 1).compareTo("0") == 0) {
                retVal += "0";
            }
        }
        return retVal;
    }

    @Override
    public void paint(GraphicsContext g2d) {
        super.paint(g2d);
    }

    @Override
    public void labelAxisX(GraphicsContext g2d) {/* do nothing per Issue #240, item 3*/}

    public void prepareExtents(boolean reScaleX, boolean reScaleY) {
        if (reScaleX) {
            minX -= 2;
            maxX += 2;
        }

        if (reScaleY || ignoreRejects) {
            double yMarginStretch = TicGeneratorForAxes.generateMarginAdjustment(minY, maxY, 0.10);
            if (yMarginStretch == 0.0) {
                yMarginStretch = yAxisData[0] / 100.0;
            }
            minY -= yMarginStretch;
            maxY += yMarginStretch;
        }
    }

    @Override
    public void plotData(GraphicsContext g2d) {
//        g2d.setFill(dataColor.color());
        g2d.setFill(Color.web(analysis.getDataHexColorString()));
//        g2d.setStroke(dataColor.color());
        g2d.setStroke(Color.web(analysis.getDataHexColorString()));
        g2d.setLineWidth(1.0);

        int cyclesPerBlock = mapBlockIdToBlockCyclesRecord.get(1).cyclesIncluded().length;
        int cycleCount = 0;
        for (int i = 0; i < xAxisData.length; i++) {
            int blockID = (int) ((xAxisData[i] - 0.7) / cyclesPerBlock) + 1;
            if (pointInPlot(xAxisData[i], yAxisData[i]) && (yAxisData[i] != 0.0)) {
//                g2d.setFill(dataColor.color());
                g2d.setFill(Color.web(analysis.getDataHexColorString()));
//                g2d.setStroke(dataColor.color());
                g2d.setStroke(Color.web(analysis.getDataHexColorString()));
                if (!analysis.getMapOfBlockIdToRawDataLiteOne().get(blockID).blockRawDataLiteIncludedArray()[cycleCount][userFunction.getColumnIndex()]) {
//                    g2d.setFill(Color.RED);
//                    g2d.setStroke(Color.RED);
                    g2d.setFill(Color.web(analysis.getAntiDataHexColorString()));
                    g2d.setStroke(Color.web(analysis.getAntiDataHexColorString()));
                }
                double dataX = mapX(xAxisData[i]);
                double dataY = mapY(yAxisData[i]);
                // TODO: refine for ratio mode
                double dataYplusSigma = mapY(yAxisData[i] + oneSigmaForCycles[i]);
                double dataYminusSigma = mapY(yAxisData[i] - oneSigmaForCycles[i]);

                if (yAxisData[i] > 0) {
                    g2d.fillOval(dataX - 2.0, dataY - 2.0, 4, 4);
                    g2d.strokeLine(dataX, dataY, dataX, dataYplusSigma);
                    g2d.strokeLine(dataX, dataY, dataX, dataYminusSigma);
                } else {
                    g2d.fillOval(dataX - 2.0, dataY - 2.0, 4, 4);
                }
            }
            cycleCount = (cycleCount + 1) % cyclesPerBlock;
        }

        if (inSculptorMode && showSelectionBox) {
            //plot selectorbox
            g2d.setStroke(Color.RED);
            g2d.setLineWidth(1.0);
            g2d.strokeRect(Math.min(mouseStartX, selectorBoxX), Math.min(mouseStartY, selectorBoxY), Math.abs(selectorBoxX - mouseStartX), Math.abs(selectorBoxY - mouseStartY));
        }

        if (inZoomBoxMode && showZoomBox) {
            g2d.setStroke(Color.BLUE);
            g2d.setLineWidth(1.5);
            g2d.strokeRect(Math.min(mouseStartX, zoomBoxX), Math.min(mouseStartY, zoomBoxY), Math.abs(zoomBoxX - mouseStartX), Math.abs(zoomBoxY - mouseStartY));
        }

        // block delimiters
        g2d.setStroke(Color.BLACK);
        g2d.setLineWidth(0.5);
        int blockID = 1;
        for (int i = 0; i < xAxisData.length; i += cyclesPerBlock) {
            if (xInPlot(xAxisData[i])) {
                double dataX = mapX(xAxisData[i] - 0.5);
                if (userFunction.getConcatenatedBlockCounts()[0] == blockID - 1) {
                    g2d.setLineWidth(2.0);
                    g2d.strokeLine(dataX, topMargin + plotHeight, dataX, topMargin);
                    g2d.setLineWidth(1.0);
                } else {
                    g2d.strokeLine(dataX, topMargin + plotHeight, dataX, topMargin);
                }
                // may 2024 issue#235
                if (blockID % 2 == 0) {
                    showBlockID(g2d, blockID, mapX(xAxisData[i + Math.abs(cyclesPerBlock / 2 - 1)]));
                }
            }
            blockID++;
        }
        double dataX = mapX(xAxisData[xAxisData.length - 1] + 0.5);
        g2d.strokeLine(dataX, topMargin + plotHeight, dataX, topMargin);

    }

    private void showBlockID(GraphicsContext g2d, int blockID, double xPosition) {
        Paint savedPaint = g2d.getFill();
        g2d.setFill(Paint.valueOf("BLACK"));

        g2d.setFont(Font.font("SansSerif", FontWeight.EXTRA_BOLD, 8));

        g2d.fillText("" + blockID, xPosition - 4, topMargin + plotHeight + 10);
        g2d.setFill(savedPaint);
    }

    /**
     *
     *
     * @param mean
     * @param stdDev
     * @param stdErr
     * @return
     */
    public HashMap<String, Double> calcPlotStatsCM(double mean, double stdDev, double stdErr) {
        double meanPlusOneStandardDeviation = mean + stdDev;
        double meanPlusTwoStandardDeviation = mean + 2.0 * stdDev;
        double meanPlusTwoStandardError = mean + 2.0 * stdErr;
        double meanMinusOneStandardDeviation = mean - stdDev;
        double meanMinusTwoStandardDeviation = mean - 2.0 * stdDev;
        double meanMinusTwoStandardError = mean - 2.0 * stdErr;

        HashMap<String, Double> output = new HashMap<>(Map.ofEntries(
                entry("mean", mean),
                entry("stdDev", stdDev),
                entry("stdErr", stdErr),
                entry("meanPlusOneStandardDeviation", meanPlusOneStandardDeviation),
                entry("meanPlusTwoStandardDeviation", meanPlusTwoStandardDeviation),
                entry("meanPlusTwoStandardError", meanPlusTwoStandardError),
                entry("meanMinusOneStandardDeviation", meanMinusOneStandardDeviation),
                entry("meanMinusTwoStandardDeviation", meanMinusTwoStandardDeviation),
                entry("meanMinusTwoStandardError", meanMinusTwoStandardError)
        ));

        return output;
    }

    public void plotStats(GraphicsContext g2d) {
        calcStats();
        BlockStatsRecord[] blockStatsRecords = analysisStatsRecord.blockStatsRecords();

        Paint saveFill = g2d.getFill();
        // TODO: promote color to constant
        g2d.setFill(Color.rgb(255, 251, 194));
        //g2d.setGlobalAlpha(0.6);

        if (blockMode) {
            int totalCycles = 0;
            int totalActualCycles = 0;
            int cyclesPerBlock = blockStatsRecords[1].cycleMeansData().length;
            for (int blockIndex = 0; blockIndex < blockStatsRecords.length; blockIndex++) {
                int cyclesPerThisBlock = blockStatsRecords[blockIndex].cycleMeansData().length;
                double leftX = mapX(xAxisData[totalCycles] - 0.5);
                if (leftX < leftMargin) leftX = leftMargin;
                double rightX = mapX(xAxisData[totalCycles + cyclesPerBlock - 1] + 0.5);
                if (rightX > leftMargin + plotWidth) rightX = leftMargin + plotWidth;

                BlockStatsRecord blockStatsRecord = blockStatsRecords[blockIndex];
                double mean = blockStatsRecords[blockIndex].mean();
                double meanPlusOneStandardDeviation = mean + blockStatsRecord.standardDeviation();
                double meanPlusTwoStandardDeviation = mean + 2.0 * blockStatsRecord.standardDeviation();
                double meanPlusTwoStandardError = mean + 2.0 * blockStatsRecord.standardError();
                double meanMinusOneStandardDeviation = mean - blockStatsRecord.standardDeviation();
                double meanMinusTwoStandardDeviation = mean - 2.0 * blockStatsRecord.standardDeviation();
                double meanMinusTwoStandardError = mean - 2.0 * blockStatsRecord.standardError();

                if (isRatio && !logScale) {
                    GeometricMeanStatsRecord geometricMeanStatsRecord = generateGeometricMeanStats(mean, blockStatsRecord.standardDeviation(), blockStatsRecord.standardError());
                    mean = geometricMeanStatsRecord.geoMean();
                    meanPlusOneStandardDeviation = geometricMeanStatsRecord.geoMeanPlusOneStdDev();
                    meanPlusTwoStandardDeviation = geometricMeanStatsRecord.geomeanPlusTwoStdDev();
                    meanPlusTwoStandardError = geometricMeanStatsRecord.geoMeanPlusTwoStdErr();
                    meanMinusOneStandardDeviation = geometricMeanStatsRecord.geoMeanMinusOneStdDev();
                    meanMinusTwoStandardDeviation = geometricMeanStatsRecord.geoMeanMinusTwoStdDev();
                    meanMinusTwoStandardError = geometricMeanStatsRecord.geoMeanMinusTwoStdErr();
                }

                double plottedTwoSigmaHeight = Math.min(mapY(meanMinusTwoStandardDeviation), topMargin + plotHeight) - Math.max(mapY(meanPlusTwoStandardDeviation), topMargin);
//                g2d.setFill(OGTRIPOLI_TWOSIGMA);
                g2d.setFill(Color.web(analysis.getTwoSigmaHexColorString()));
                g2d.fillRect(leftX, Math.max(mapY(meanPlusTwoStandardDeviation), topMargin), rightX - leftX, plottedTwoSigmaHeight);

                double plottedOneSigmaHeight = Math.min(mapY(meanMinusOneStandardDeviation), topMargin + plotHeight) - Math.max(mapY(meanPlusOneStandardDeviation), topMargin);
//                g2d.setFill(OGTRIPOLI_ONESIGMA);
                g2d.setFill(Color.web(analysis.getOneSigmaHexColorString()));
                g2d.fillRect(leftX, Math.max(mapY(meanPlusOneStandardDeviation), topMargin), rightX - leftX, plottedOneSigmaHeight);

                double plottedTwoStdErrHeight = Math.min(mapY(meanMinusTwoStandardError), topMargin + plotHeight) - Math.max(mapY(meanPlusTwoStandardError), topMargin);
//                g2d.setFill(OGTRIPOLI_TWOSTDERR);
                g2d.setFill(Color.web(analysis.getTwoStandardErrorHexColorString()));
                g2d.fillRect(leftX, Math.max(mapY(meanPlusTwoStandardError), topMargin), rightX - leftX, plottedTwoStdErrHeight);

                boolean meanIsPlottable = (mapY(mean) >= topMargin) && (mapY(mean) <= topMargin + plotHeight);
                if (meanIsPlottable && (leftX <= rightX)) {
//                    g2d.setStroke(OGTRIPOLI_MEAN);
                    g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
                    g2d.setLineWidth(1.5);
                    g2d.strokeLine(leftX, mapY(mean), rightX, mapY(mean));
                }
                totalCycles = totalCycles + cyclesPerBlock;
                totalActualCycles = totalActualCycles + cyclesPerThisBlock;
            }

        } else { //cycle mode
            HashMap<String, Double> plotStats = calcPlotStatsCM(
                    analysisStatsRecord.cycleModeMean(),
                    analysisStatsRecord.cycleModeStandardDeviation(),
                    analysisStatsRecord.cycleModeStandardError());

            double mean = plotStats.get("mean");
            double stdDev = plotStats.get("stdDev");
            double stdErr = plotStats.get("stdErr");
            double meanPlusOneStandardDeviation = plotStats.get("meanPlusOneStandardDeviation");
            double meanPlusTwoStandardDeviation = plotStats.get("meanPlusTwoStandardDeviation");
            double meanPlusTwoStandardError = plotStats.get("meanPlusTwoStandardError");
            double meanMinusOneStandardDeviation = plotStats.get("meanMinusOneStandardDeviation");
            double meanMinusTwoStandardDeviation = plotStats.get("meanMinusTwoStandardDeviation");
            double meanMinusTwoStandardError = plotStats.get("meanMinusTwoStandardError");

            double leftX = mapX(xAxisData[0] - 0.5);
            if (leftX < leftMargin) leftX = leftMargin;
            double rightX = mapX(xAxisData[xAxisData.length - 1] + 0.5);
            if (rightX > leftMargin + plotWidth) rightX = leftMargin + plotWidth;

            if (isRatio && !logScale) {
                GeometricMeanStatsRecord geometricMeanStatsRecord = generateGeometricMeanStats(mean, stdDev, stdErr);
                mean = geometricMeanStatsRecord.geoMean();
                meanPlusOneStandardDeviation = geometricMeanStatsRecord.geoMeanPlusOneStdDev();
                meanPlusTwoStandardDeviation = geometricMeanStatsRecord.geomeanPlusTwoStdDev();
                meanPlusTwoStandardError = geometricMeanStatsRecord.geoMeanPlusTwoStdErr();
                meanMinusOneStandardDeviation = geometricMeanStatsRecord.geoMeanMinusOneStdDev();
                meanMinusTwoStandardDeviation = geometricMeanStatsRecord.geoMeanMinusTwoStdDev();
                meanMinusTwoStandardError = geometricMeanStatsRecord.geoMeanMinusTwoStdErr();
            }

            double plottedTwoSigmaHeight = Math.min(mapY(meanMinusTwoStandardDeviation), topMargin + plotHeight) - Math.max(mapY(meanPlusTwoStandardDeviation), topMargin);
//            g2d.setFill(OGTRIPOLI_TWOSIGMA);
            g2d.setFill(Color.web(analysis.getTwoSigmaHexColorString()));
            g2d.fillRect(leftX, Math.max(mapY(meanPlusTwoStandardDeviation), topMargin), rightX - leftX, plottedTwoSigmaHeight);

            double plottedOneSigmaHeight = Math.min(mapY(meanMinusOneStandardDeviation), topMargin + plotHeight) - Math.max(mapY(meanPlusOneStandardDeviation), topMargin);
//            g2d.setFill(OGTRIPOLI_ONESIGMA);
            g2d.setFill(Color.web(analysis.getOneSigmaHexColorString()));
            g2d.fillRect(leftX, Math.max(mapY(meanPlusOneStandardDeviation), topMargin), rightX - leftX, plottedOneSigmaHeight);

            double plottedTwoStdErrHeight = Math.min(mapY(meanMinusTwoStandardError), topMargin + plotHeight) - Math.max(mapY(meanPlusTwoStandardError), topMargin);
//            g2d.setFill(OGTRIPOLI_TWOSTDERR);
            g2d.setFill(Color.web(analysis.getTwoStandardErrorHexColorString()));
            g2d.fillRect(leftX, Math.max(mapY(meanPlusTwoStandardError), topMargin), rightX - leftX, plottedTwoStdErrHeight);

            boolean meanIsPlottable = (mapY(mean) >= topMargin) && (mapY(mean) <= topMargin + plotHeight);
            if (meanIsPlottable && (leftX <= rightX)) {
//                g2d.setStroke(OGTRIPOLI_MEAN);
                g2d.setStroke(Color.web(analysis.getMeanHexColorString()));
                g2d.setLineWidth(1.5);
                g2d.strokeLine(leftX - 2, mapY(mean), rightX + 2, mapY(mean));
            }
        }
        g2d.setFill(saveFill);
        g2d.setGlobalAlpha(1.0);
    }

    public void setupPlotContextMenu() {
        // no menu for now
        plotContextMenu = new ContextMenu();
    }

    public void resetData() {
        for (int i = 0; i < mapBlockIdToBlockCyclesRecord.size(); i++) {
            mapBlockIdToBlockCyclesRecord.put(i + 1, mapBlockIdToBlockCyclesRecord.get(i + 1).resetAllDataIncluded());
            analysis.getMapOfBlockIdToRawDataLiteOne().put(i + 1, analysis.getMapOfBlockIdToRawDataLiteOne().get(i + 1).resetAllDataIncluded(userFunction));
        }
        repaint();
    }

    @Override
    public void performChauvenets() {
        if (blockMode) {
            for (int i = 0; i < mapBlockIdToBlockCyclesRecord.size(); i++) {
                int blockID = i + 1;
                PlotBlockCyclesRecord plotBlockCyclesRecord = mapBlockIdToBlockCyclesRecord.get(blockID).performChauvenets(analysis.getParameters());
                mapBlockIdToBlockCyclesRecord.put(blockID, plotBlockCyclesRecord);
                analysis.getMapOfBlockIdToRawDataLiteOne().put(blockID,
                        analysis.getMapOfBlockIdToRawDataLiteOne().get(i + 1).recordChauvenets(userFunction, plotBlockCyclesRecord.cyclesIncluded()));

                boolean[] plotBlockCyclesIncluded = analysis.getMapOfBlockIdToRawDataLiteOne().get(blockID).assembleCyclesIncludedForUserFunction(userFunction);
                mapBlockIdToBlockCyclesRecord.put(
                        blockID,
                        getMapBlockIdToBlockCyclesRecord().get(blockID).updateCyclesIncluded(plotBlockCyclesIncluded));
            }
        } else {
            // cycle mode
            boolean[] cycleModeIncluded = analysisStatsRecord.cycleModeIncluded();
            double[] cycleModeData = analysisStatsRecord.cycleModeData();
//            if (Booleans.countTrue(cycleModeIncluded) == cycleModeIncluded.length) {
                boolean[] chauvenets = applyChauvenetsCriterion(
                        cycleModeData,
                        cycleModeIncluded,
                        analysis.getParameters());
                // reset included cycles for each block
                BlockStatsRecord[] blockStatsRecords = analysisStatsRecord.blockStatsRecords();
                int countOfProcessedCycles = 0;
                for (int i = 0; i < blockStatsRecords.length; i++) {
                    System.arraycopy(chauvenets, countOfProcessedCycles,
                            blockStatsRecords[i].cyclesIncluded(), 0, blockStatsRecords[i].cyclesIncluded().length);
                    countOfProcessedCycles += blockStatsRecords[i].cyclesIncluded().length;
                    int blockID = i + 1;
                    PlotBlockCyclesRecord plotBlockCyclesRecord = mapBlockIdToBlockCyclesRecord.get(blockID);
                    plotBlockCyclesRecord.updateCyclesIncluded(blockStatsRecords[i].cyclesIncluded());
                    mapBlockIdToBlockCyclesRecord.put(blockID, plotBlockCyclesRecord);
                    analysis.getMapOfBlockIdToRawDataLiteOne().put(blockID,
                            analysis.getMapOfBlockIdToRawDataLiteOne().get(i + 1).recordChauvenets(userFunction, plotBlockCyclesRecord.cyclesIncluded()));

                }
                analysisStatsRecord = AnalysisStatsRecord.generateAnalysisStatsRecord(blockStatsRecords);
//            }
        }

        repaint();
    }

    public boolean detectAllIncludedStatus() {
        boolean retVal = true;
        for (int i = 0; i < mapBlockIdToBlockCyclesRecord.size(); i++) {
            retVal = retVal && mapBlockIdToBlockCyclesRecord.get(i + 1).detectAllIncludedStatus();
        }
        return retVal;
    }

    private int determineSculptBlock(double mouseX) {
        double mouseTime = convertMouseXToValue(mouseX);
        int xAxisIndexOfMouse = Math.min(xAxisData.length - 1, (int) Math.round(mouseTime));
        double t0 = xAxisData[xAxisIndexOfMouse];
        double t2 = xAxisData[(xAxisIndexOfMouse >= 2) ? (xAxisIndexOfMouse - 2) : 0];
        int sculptBlockIDCalc = blockIDsPerTimeSlot[(xAxisIndexOfMouse >= 1) ? (xAxisIndexOfMouse - 1) : 0];
        if (((t0 - t2) > 5.0) && (Math.abs(mouseTime - t2) > Math.abs(mouseTime - t0))) {
            sculptBlockIDCalc = blockIDsPerTimeSlot[xAxisIndexOfMouse];
        }

        if (sculptBlockIDCalc == 0) sculptBlockIDCalc = mapBlockIdToBlockCyclesRecord.size();
        return sculptBlockIDCalc;
    }

    public Map<Integer, PlotBlockCyclesRecord> getMapBlockIdToBlockCyclesRecord() {
        return mapBlockIdToBlockCyclesRecord;
    }

    /**
     * @return
     */
    @Override
    public AnalysisBlockCyclesRecord getAnalysisBlockCyclesRecord() {
        return null;
    }

    public UserFunction getUserFunction() {
        return userFunction;
    }

    private boolean mouseInBlockLabel(double sceneX, double sceneY) {
        return ((sceneX >= leftMargin)
                && (sceneY >= topMargin - 15)
                && (sceneY < topMargin)
                && (sceneX < (plotWidth + leftMargin - 2)));
    }

    public void sculptBlock(boolean zoomBlock) {
        if ((0 < sculptBlockID) && !inSculptorMode) {
            inSculptorMode = true;
            showSelectionBox = true;
            setOnMouseDragged(new AnalysisBlockCyclesPlotOG.MouseDraggedEventHandlerSculpt());
            setOnMousePressed(new AnalysisBlockCyclesPlotOG.MousePressedEventHandlerSculpt());
            setOnMouseReleased(new AnalysisBlockCyclesPlotOG.MouseReleasedEventHandlerSculpt());
            selectorBoxX = mouseStartX;
            selectorBoxY = mouseStartY;

            // zoom into block
            countOfPreviousBlockIncludedData = (sculptBlockID - 1) * mapBlockIdToBlockCyclesRecord.get(1).cyclesIncluded().length;
            if (zoomBlock) {
                displayOffsetX = 0;
                int countOfCycles = mapBlockIdToBlockCyclesRecord.get(sculptBlockID).cyclesIncluded().length;
                minX = xAxisData[countOfPreviousBlockIncludedData] - 1;
                maxX = xAxisData[countOfPreviousBlockIncludedData
                        + countOfCycles - 1] + 1;

                minY = Double.MAX_VALUE;
                maxY = -Double.MAX_VALUE;
                for (int i = countOfPreviousBlockIncludedData; i < countOfPreviousBlockIncludedData + countOfCycles; i++) {
                    if (0.0 != yAxisData[i]) {
                        minY = min(minY, yAxisData[i]);
                        maxY = max(maxY, yAxisData[i]);
                    }
                }
                double yMarginStretch = TicGeneratorForAxes.generateMarginAdjustment(minY, maxY, 0.05);
                maxY += yMarginStretch;
                minY -= yMarginStretch;
                displayOffsetY = 0.0;

                refreshPanel(false, false);
            }
        } else {
            inSculptorMode = false;
            showSelectionBox = false;
            setOnMouseDragged(new AnalysisBlockCyclesPlotOG.MouseDraggedEventHandler());
            setOnMousePressed(new AnalysisBlockCyclesPlotOG.MousePressedEventHandler());
            setOnMouseReleased(new AnalysisBlockCyclesPlotOG.MouseReleasedEventHandler());
        }
        repaint();
    }

    private void exitSculptingMode() {
        inSculptorMode = false;
        sculptBlockID = 0;
        inZoomBoxMode = true;
        showZoomBox = true;
        zoomBoxX = mouseStartX;
        zoomBoxY = mouseStartY;
        refreshPanel(true, true);
        ((TripoliPlotPane) getParent().getParent()).removeSculptingHBox();
        tooltip.setText(tooltipTextSculpt);
    }

    private void enterSculptingMode() {
        inSculptorMode = false;
        inZoomBoxMode = false;
        showZoomBox = false;
        ((TripoliPlotPane) getParent().getParent()).removeSculptingHBox();
        sculptBlockID = 1;//determineSculptBlock(mouseEvent.getX());
        ((TripoliPlotPane) getParent().getParent()).builtSculptingHBox(
                "Cycle Sculpting " + "  >> " + tooltipTextExitSculpt);
        sculptBlock(false);//mouseInBlockLabel(mouseEvent.getX(), mouseEvent.getY()));
        inSculptorMode = true;
        tooltip.setText(tooltipTextExitSculpt);
    }

    public boolean isIgnoreRejects() {
        return ignoreRejects;
    }

    public void setIgnoreRejects(boolean ignoreRejects) {
        this.ignoreRejects = ignoreRejects;
    }

    public void toggleSculptingMode() {
        if (inSculptorMode) {
            exitSculptingMode();
        } else {
            enterSculptingMode();
        }
    }

    class MouseClickEventHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent mouseEvent) {
            boolean isPrimary = (0 == mouseEvent.getButton().compareTo(MouseButton.PRIMARY));
            if (2 == mouseEvent.getClickCount() && !mouseEvent.isControlDown()) {
                if (isPrimary && (mouseInHouse(mouseEvent.getX(), mouseEvent.getY()) || mouseInBlockLabel(mouseEvent.getX(), mouseEvent.getY()))) {
                    if (inSculptorMode) {
                        exitSculptingMode();
                    } else {
                        enterSculptingMode();
                    }
                }
            } else {
                if (isPrimary && mouseEvent.isControlDown() && (mouseInHouse(mouseEvent.getX(), mouseEvent.getY()))) {
                    // turn off / on block
                    sculptBlockID = determineSculptBlock(mouseEvent.getX());
                    mapBlockIdToBlockCyclesRecord.put(sculptBlockID,
                            mapBlockIdToBlockCyclesRecord.get(sculptBlockID).toggleBlockIncluded());
                    analysis.getMapOfBlockIdToRawDataLiteOne().put(sculptBlockID,
                            analysis.getMapOfBlockIdToRawDataLiteOne().get(sculptBlockID).toggleAllDataIncludedUserFunction(userFunction));

                    inZoomBoxMode = !inSculptorMode;
                    showZoomBox = !inSculptorMode;
                    refreshPanel(false, false);
                } else if (!isPrimary && mouseEvent.isControlDown() && (mouseInHouse(mouseEvent.getX(), mouseEvent.getY()))) {
                    // zoom block
                    sculptBlockID = determineSculptBlock(mouseEvent.getX());
                    ((TripoliPlotPane) getParent().getParent()).removeSculptingHBox();
                    ((TripoliPlotPane) getParent().getParent()).builtSculptingHBox(
                            "Cycle Sculpting " + "  >> " + tooltipTextExitSculpt);
                    sculptBlock(true);
                    inZoomBoxMode = !inSculptorMode;
                    showZoomBox = !inSculptorMode;

                    tooltip.setText(tooltipTextExitSculpt);
                    repaint();
                }
            }
        }
    }

    class MouseDraggedEventHandlerSculpt implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            if (e.isPrimaryButtonDown()) {
                if (mouseInHouse(e.getX(), e.getY())) {
                    selectorBoxX = e.getX();
                    selectorBoxY = e.getY();
                    showSelectionBox = true;
                }
            } else {
                showSelectionBox = false;
                displayOffsetX = displayOffsetX + (convertMouseXToValue(mouseStartX) - convertMouseXToValue(e.getX()));
                displayOffsetY = displayOffsetY + (convertMouseYToValue(mouseStartY) - convertMouseYToValue(e.getY()));
                adjustMouseStartsForPress(e.getX(), e.getY());
                calculateTics();
            }
            repaint();
        }
    }

    class MouseReleasedEventHandlerSculpt implements EventHandler<MouseEvent> {
        /**
         * @param e the event which occurred
         */
        @Override
        public void handle(MouseEvent e) {
            boolean isPrimary = (0 == e.getButton().compareTo(MouseButton.PRIMARY));
            if (mouseInHouse(e.getX(), e.getY()) && isPrimary) {
                showSelectionBox = true;
                // process contained data points
                selectorBoxX = e.getX();
                selectorBoxY = e.getY();
                double timeLeft = convertMouseXToValue(Math.min(mouseStartX, selectorBoxX));
                double timeRight = convertMouseXToValue(Math.max(mouseStartX, selectorBoxX));
                int indexLeft = Math.max(1, Math.abs(binarySearch(xAxisData, timeLeft))) - 1;
                int indexRight = Math.max(2, Math.abs(binarySearch(xAxisData, timeRight))) - 2;
                if (indexRight < indexLeft) {
                    indexRight = indexLeft;
                }

                double intensityTop = convertMouseYToValue(Math.min(mouseStartY, selectorBoxY));
                double intensityBottom = convertMouseYToValue(Math.max(mouseStartY, selectorBoxY));

                int expectedCyclesCount = mapBlockIdToBlockCyclesRecord.get(1).cycleMeansData().length;

                int sculptBlockIDStart = blockIDsPerTimeSlot[min(indexLeft, blockIDsPerTimeSlot.length - 1)];
                int sculptBlockIDEnd = blockIDsPerTimeSlot[min(indexRight, blockIDsPerTimeSlot.length - 1)];
                if (sculptBlockIDEnd == 0) sculptBlockIDEnd = blockIDsPerTimeSlot[indexRight - expectedCyclesCount];
                // calculate majority for multi-select
                List<Boolean> statusList = new ArrayList<>();
                for (int sculptBlockCurrent = sculptBlockIDStart; sculptBlockCurrent <= sculptBlockIDEnd; sculptBlockCurrent++) {
                    double[] data;
                    if (userFunction.isInverted()) {
                        data = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).invertedCycleMeansData();
                    } else {
                        data = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).cycleMeansData();
                    }
                    boolean[] cyclesIncluded = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).cyclesIncluded().clone();

                    int startLeft = max(0, (indexLeft - (sculptBlockCurrent - 1) * expectedCyclesCount) % cyclesIncluded.length);
                    int endRight = min(data.length - 1, (indexRight - (sculptBlockCurrent - 1) * expectedCyclesCount));

                    for (int i = startLeft; i <= endRight; i++) {
                        if ((data[i] <= intensityTop) && (data[i] >= intensityBottom)) {
                            statusList.add(cyclesIncluded[i]);
                        }
                    }
                }

                boolean[] status = Booleans.toArray(statusList);
                int countIncluded = Booleans.countTrue(status);
                boolean majorityIncludedValue = countIncluded > status.length / 2;

                for (int sculptBlockCurrent = sculptBlockIDStart; sculptBlockCurrent <= sculptBlockIDEnd; sculptBlockCurrent++) {
                    double[] data;
                    if (userFunction.isInverted()) {
                        data = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).invertedCycleMeansData();
                    } else {
                        data = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).cycleMeansData();
                    }
                    boolean[] cyclesIncluded = mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).cyclesIncluded().clone();

                    int startLeft = max(0, (indexLeft - (sculptBlockCurrent - 1) * expectedCyclesCount) % cyclesIncluded.length);
                    int endRight = min(data.length - 1, (indexRight - (sculptBlockCurrent - 1) * expectedCyclesCount));

                    for (int i = startLeft; i <= endRight; i++) {
                        if ((data[i] <= intensityTop) && (data[i] >= intensityBottom)) {
                            cyclesIncluded[i] = !majorityIncludedValue;
                        }
                    }
                    mapBlockIdToBlockCyclesRecord.put(sculptBlockCurrent,
                            mapBlockIdToBlockCyclesRecord.get(sculptBlockCurrent).updateCyclesIncluded(cyclesIncluded));
                    analysis.getMapOfBlockIdToRawDataLiteOne().put(sculptBlockCurrent,
                            analysis.getMapOfBlockIdToRawDataLiteOne().get(sculptBlockCurrent).updateIncludedCycles(userFunction, cyclesIncluded));
                }

            } else {
                showSelectionBox = false;
            }
            adjustMouseStartsForPress(e.getX(), e.getY());
            selectorBoxX = mouseStartX;
            selectorBoxY = mouseStartY;

            refreshPanel(false, false);
        }
    }

    class MousePressedEventHandlerSculpt implements EventHandler<MouseEvent> {
        /**
         * @param e the event which occurred
         */
        @Override
        public void handle(MouseEvent e) {
            if (mouseInHouse(e.getX(), e.getY()) && e.isPrimaryButtonDown()) {
                showSelectionBox = true;
                adjustMouseStartsForPress(e.getX(), e.getY());
                selectorBoxX = mouseStartX;
                selectorBoxY = mouseStartY;
                sculptBlockID = determineSculptBlock(e.getX());
                inSculptorMode = false;
                sculptBlock(false);
            } else {
                showSelectionBox = false;
                adjustMouseStartsForPress(e.getX(), e.getY());
            }
        }
    }

    class MouseDraggedEventHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            if (inZoomBoxMode && mouseInHouse(e.getX(), e.getY()) && e.isPrimaryButtonDown()) {
                zoomBoxX = e.getX();
                zoomBoxY = e.getY();
                showZoomBox = true;

            } else {
                if (mouseInHouse(e.getX(), e.getY()) && !e.isPrimaryButtonDown() && !e.isControlDown()) {
                    // right mouse PAN
                    showZoomBox = false;
                    displayOffsetX = displayOffsetX + (convertMouseXToValue(mouseStartX) - convertMouseXToValue(e.getX()));
                    displayOffsetY = displayOffsetY + (convertMouseYToValue(mouseStartY) - convertMouseYToValue(e.getY()));
                    adjustMouseStartsForPress(e.getX(), e.getY());
                    calculateTics();
                }
            }
            repaint();
        }
    }

    class MousePressedEventHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            if (mouseInHouse(e.getX(), e.getY()) && !e.isPrimaryButtonDown()) {
                adjustMouseStartsForPress(e.getX(), e.getY());
                inZoomBoxMode = false;
                showZoomBox = false;
            } else if (mouseInHouse(e.getX(), e.getY()) && e.isPrimaryButtonDown()) {
                inZoomBoxMode = true;
                showZoomBox = true;
                adjustMouseStartsForPress(e.getX(), e.getY());
                zoomBoxX = mouseStartX;
                zoomBoxY = mouseStartY;
            }
        }
    }

    class MouseReleasedEventHandler implements EventHandler<MouseEvent> {
        /**
         * @param e the event which occurred
         */
        @Override
        public void handle(MouseEvent e) {
            if (inZoomBoxMode && mouseInHouse(e.getX(), e.getY())) {
                showZoomBox = true;
                zoomBoxX = e.getX();
                zoomBoxY = e.getY();
                if ((zoomBoxX != mouseStartX) && (zoomBoxY != mouseStartY)) {
                    double timeLeft = convertMouseXToValue(Math.min(mouseStartX, zoomBoxX));
                    double timeRight = convertMouseXToValue(Math.max(mouseStartX, zoomBoxX));
                    int indexLeft = Math.max(1, Math.abs(binarySearch(xAxisData, timeLeft))) - 1;
                    int indexRight = Math.max(2, Math.abs(binarySearch(xAxisData, timeRight))) - 2;
                    if (indexRight < indexLeft) {
                        indexRight = indexLeft;
                    }
                    double intensityTop = convertMouseYToValue(Math.min(mouseStartY, zoomBoxY));
                    double intensityBottom = convertMouseYToValue(Math.max(mouseStartY, zoomBoxY));

                    displayOffsetX = xAxisData[indexLeft] - minX - 2;
                    maxX = xAxisData[(indexRight == xAxisData.length - 1) ? indexRight : indexRight + 1] - displayOffsetX;

                    minY = intensityBottom;
                    maxY = intensityTop;

                    double yMarginStretch = TicGeneratorForAxes.generateMarginAdjustment(minY, maxY, 0.05);
                    maxY += yMarginStretch;
                    minY -= yMarginStretch;
                    displayOffsetY = 0.0;

                    inZoomBoxMode = false;
                    showZoomBox = false;
                    ((TripoliPlotPane) getParent().getParent()).removeSculptingHBox();
                    ((TripoliPlotPane) getParent().getParent()).builtSculptingHBox(
                            "Cycle Sculpting " + "  >> " + tooltipTextExitSculpt);
                    inSculptorMode = true;
                    showSelectionBox = false;
                    setOnMouseDragged(new AnalysisBlockCyclesPlotOG.MouseDraggedEventHandlerSculpt());
                    setOnMousePressed(new AnalysisBlockCyclesPlotOG.MousePressedEventHandlerSculpt());
                    setOnMouseReleased(new AnalysisBlockCyclesPlotOG.MouseReleasedEventHandlerSculpt());
                    selectorBoxX = mouseStartX;
                    selectorBoxY = mouseStartY;

                    refreshPanel(false, false);
                }
                adjustMouseStartsForPress(e.getX(), e.getY());
                zoomBoxX = mouseStartX;
                zoomBoxY = mouseStartY;

            } else {
                zoomBoxX = mouseStartX;
                zoomBoxY = mouseStartY;
            }
            repaint();
        }
    }
}