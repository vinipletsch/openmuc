/*
 * Copyright 2011-14 Fraunhofer ISE
 *
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * OpenMUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenMUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenMUC.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.framework.driver.knx.value;

import org.openmuc.framework.data.LongValue;
import org.openmuc.framework.data.Value;
import tuwien.auto.calimero.dptxlator.DPTXlatorDate;
import tuwien.auto.calimero.exception.KNXFormatException;

/**
 * @author frobra
 */
public class KnxValueDate extends KnxValue {

    /**
     * @param dptID
     * @throws KNXFormatException
     */
    public KnxValueDate(String dptID) throws KNXFormatException {
        dptXlator = new DPTXlatorDate(dptID);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openmuc.framework.driver.knx.value.KnxValue#setOpenMucValue(org.openmuc.framework.data.Value)
     */
    @Override
    public void setOpenMucValue(Value value) throws KNXFormatException {
        ((DPTXlatorDate) dptXlator).setValue(value.asLong());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openmuc.framework.driver.knx.value.KnxValue#getOpenMucValue()
     */
    @Override
    public Value getOpenMucValue() {
        try {
            return new LongValue(((DPTXlatorDate) dptXlator).getValueMilliseconds());
        }
        catch (Exception e) {
            return new LongValue(0);
        }
    }

}
