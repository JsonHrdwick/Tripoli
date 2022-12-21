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

package org.cirdles.tripoli.sessions.analysis.methods.baseline;

import java.io.Serializable;

/**
 * @author James F. Bowring
 * <p>
 * BaselineCell holds a mass only.
 */
public class BaselineCell implements Serializable {

    private String baselineName;
    private int baselineIndex;
    private double cellMass;

    public BaselineCell(String baselineName, int baselineIndex) {
        this.baselineName = baselineName;
        this.baselineIndex = baselineIndex;
        this.cellMass = 0.0;
    }

    public static BaselineCell initializeBaselineCell(String baselineName, int baselineIndex) {
        return new BaselineCell(baselineName, baselineIndex);
    }

    @Override
    public boolean equals(Object otherObject) {
        boolean retVal = true;
        if (otherObject != this) {
            if (otherObject instanceof BaselineCell otherBaselineCell) {
                retVal = this.getBaselineName().compareToIgnoreCase(otherBaselineCell.getBaselineName()) == 0;
            } else {
                retVal = false;
            }
        }
        return retVal;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (baselineName == null ? 0 : baselineName.hashCode());
        return hash;
    }

    public String getBaselineName() {
        return baselineName;
    }

    public void setBaselineName(String baselineName) {
        this.baselineName = baselineName;
    }

    public int getBaselineIndex() {
        return baselineIndex;
    }

    public void setBaselineIndex(int baselineIndex) {
        this.baselineIndex = baselineIndex;
    }

    public double getCellMass() {
        return cellMass;
    }

    public void setCellMass(double cellMass) {
        this.cellMass = cellMass;
    }
}