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
package com.hartrusion.rbmksim;

import java.util.ArrayList;
import java.util.List;

/**
 * AZ-5 insertion that keeps the number of manual rods which have not yet
 * passed {@link DisplacerAccident#WINDOW_HIGH_M} at or below
 * {@link DisplacerAccident#SIMULTANEOUS_LIMIT}. Automatic rods, which do not
 * contribute displacer boost, still drive in immediately. Short rods keep
 * the movement rules from the original shutdown path.
 */
final class StagedAz5 {

    private boolean active;
    private final List<ControlRod> waiting = new ArrayList<>();
    private final List<ControlRod> moving = new ArrayList<>();

    boolean isActive() {
        return active;
    }

    void cancel() {
        active = false;
        waiting.clear();
        moving.clear();
    }

    void begin(List<ControlRod> rods) {
        active = true;
        waiting.clear();
        moving.clear();
        List<ControlRod> alreadyInWindow = new ArrayList<>();
        for (ControlRod rod : rods) {
            rod.setAutomatic(false);
            ChannelType type = rod.getRodType();
            if (type == ChannelType.SHORT_CONTROLROD) {
                rod.rodSpeedMax();
                if (rod.getSwi().getOutput() <= 2.8) {
                    rod.getSwi().setStop();
                } else {
                    rod.getSwi().setInputMin();
                }
            } else if (type == ChannelType.MANUAL_CONTROLROD) {
                double position = rod.getSwi().getOutput();
                if (position > DisplacerAccident.WINDOW_HIGH_M) {
                    commandInsert(rod);
                } else if (position > DisplacerAccident.WINDOW_LOW_M) {
                    alreadyInWindow.add(rod);
                } else {
                    rod.getSwi().setStop();
                    waiting.add(rod);
                }
            } else {
                commandInsert(rod);
            }
        }
        for (ControlRod rod : alreadyInWindow) {
            commandInsert(rod);
            moving.add(rod);
        }
        releaseSlots();
        if (moving.isEmpty() && waiting.isEmpty()) {
            active = false;
        }
    }

    /**
     * Call once per core step, before the rods integrate their setpoints.
     */
    void advance() {
        if (!active) {
            return;
        }
        moving.removeIf(rod -> rod.getSwi().getOutput() > DisplacerAccident.WINDOW_HIGH_M);
        releaseSlots();
        if (moving.isEmpty() && waiting.isEmpty()) {
            active = false;
        }
    }

    private void releaseSlots() {
        int slots = DisplacerAccident.insertionSlots(moving.size());
        while (slots > 0 && !waiting.isEmpty()) {
            ControlRod rod = waiting.remove(0);
            commandInsert(rod);
            moving.add(rod);
            slots--;
        }
    }

    private static void commandInsert(ControlRod rod) {
        rod.rodSpeedMax();
        rod.getSwi().setInputMax();
    }
}
