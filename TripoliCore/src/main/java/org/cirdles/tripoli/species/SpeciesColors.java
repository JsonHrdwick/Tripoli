package org.cirdles.tripoli.species;

import static org.cirdles.tripoli.constants.TripoliConstants.DetectorPlotFlavor;

import java.io.Serializable;


public record SpeciesColors(
        String faradayHexColor,
        String pmHexColor,
        String faradayModelHexColor,
        String pmModelHexColor) implements Serializable {

        public String get(DetectorPlotFlavor plotFlavor) {
            String result = "";
            switch (plotFlavor) {
                case PM_DATA -> result=pmHexColor;
                case FARADAY_DATA -> result=faradayHexColor;
                case FARADAY_MODEL -> result=faradayModelHexColor;
                case PM_MODEL -> result=pmModelHexColor();
            }
            return result;
        }

        public SpeciesColors copy() {
            return new SpeciesColors(
                    new String(faradayHexColor),
                    new String(pmHexColor),
                    new String(faradayModelHexColor),
                    new String(pmModelHexColor()));
        }
        public SpeciesColors altered(DetectorPlotFlavor plotFlavor, String hexColor) {
            String faraday = faradayHexColor;
            String pm  = pmHexColor;
            String faradayModel = faradayModelHexColor;
            String pmModel = pmModelHexColor;
            switch (plotFlavor) {
                case PM_DATA -> {
                    pm = hexColor;
                }
                case PM_MODEL -> {
                    pmModel = hexColor;
                }
                case FARADAY_DATA -> {
                    faraday = hexColor;
                }
                case FARADAY_MODEL -> {
                    faradayModel = hexColor;
                }
            }
            return new SpeciesColors(faraday, pm, faradayModel, pmModel);
        }
}
