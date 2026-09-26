/*
 * Copyright (C) 2026 RBMK Simulator contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.rbmksim.jev;

/**
 * Plant values the AZ-5 guard sends to Jev. All of them are already computed
 * by the simulator. Nothing here is an extra physics estimate.
 *
 * @param neutronFluxPercent neutron flux in the model's percent-of-nominal units
 * @param manualRodsWithdrawn manual rods still above the displacer window
 * @param manualRodsInWindow manual rods inside the displacer window
 * @param manualRodsTotal manual rods on this core
 * @param ormEquivalentRods operational reactivity margin, in equivalent rods
 */
public record Az5PlantState(
        double neutronFluxPercent,
        int manualRodsWithdrawn,
        int manualRodsInWindow,
        int manualRodsTotal,
        double ormEquivalentRods) {

    public int manualRodsNotYetClear() {
        return manualRodsWithdrawn + manualRodsInWindow;
    }
}
