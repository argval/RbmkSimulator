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
package com.hartrusion.rbmksim.gui;

import com.hartrusion.mvc.UpdateReceiver;
import com.hartrusion.rbmksim.DisplacerAccident;
import com.hartrusion.rbmksim.jev.Az5Decision;
import com.hartrusion.rbmksim.jev.Az5Guard;
import com.hartrusion.rbmksim.jev.Az5PlantState;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * Control-panel readout of the AZ-5 plant values sent to Jev, and the answer
 * after the guard call. Styled like the other internal frames (closable,
 * iconifiable, resizable) with monospaced dark readouts in the same family as
 * Reactor Controls flux/ORM digits.
 */
public class InternalFrameJevValues extends javax.swing.JInternalFrame
        implements UpdateReceiver {

    private static final Color READOUT_BG = new Color(51, 51, 51);
    private static final Color READOUT_FG = new Color(0, 255, 153);
    private static final Font READOUT_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private static final Font CAPTION_FONT = new Font(Font.DIALOG, Font.PLAIN, 11);
    private static final Font SECTION_FONT = new Font(Font.DIALOG, Font.BOLD, 12);
    private static final String WAITING = "—";

    private final JLabel guardStatus = valueLabel();
    private final JLabel fluxPercent = valueLabel();
    private final JLabel fluxMultiplier = valueLabel();
    private final JLabel rodsWithdrawn = valueLabel();
    private final JLabel rodsInWindow = valueLabel();
    private final JLabel rodsNotYetClear = valueLabel();
    private final JLabel rodsTotal = valueLabel();
    private final JLabel ormEquivalent = valueLabel();

    private final JLabel decisionSource = valueLabel();
    private final JLabel decisionModel = valueLabel();
    private final JLabel spikeNoul = valueLabel();
    private final JLabel scramChoice = valueLabel();
    private final JLabel choiceConfidence = valueLabel();
    private final JLabel insertionStaged = valueLabel();

    public InternalFrameJevValues() {
        initFrame();
        applyGuardStatus();
        clearDecision();
    }

    private void initFrame() {
        setClosable(true);
        setIconifiable(true);
        setResizable(true);
        setTitle("Jev AZ-5 Guard");
        setPreferredSize(new java.awt.Dimension(340, 420));

        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 6, 0);
        root.add(statusRow(), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 8, 0);
        root.add(inputsSection(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        root.add(decisionSection(), gbc);

        getContentPane().add(root);
        pack();
    }

    private JPanel statusRow() {
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 8);
        JLabel caption = captionLabel("Guard");
        caption.setFont(SECTION_FONT);
        row.add(caption, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(guardStatus, gbc);
        return row;
    }

    private JPanel inputsSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Inputs to Jev",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                SECTION_FONT));
        int row = 0;
        row = addRow(section, row, "Neutron flux %", fluxPercent);
        row = addRow(section, row, "Flux multiplier", fluxMultiplier);
        row = addRow(section, row, "Rods not yet clear of 1.25 m", rodsNotYetClear);
        row = addRow(section, row, "Rods in 0.75–1.25 m window", rodsInWindow);
        row = addRow(section, row, "Rods withdrawn above window", rodsWithdrawn);
        row = addRow(section, row, "Manual rods total", rodsTotal);
        addRow(section, row, "ORM (equiv. rods)", ormEquivalent);
        return section;
    }

    private JPanel decisionSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "After AZ-5 guard call",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                SECTION_FONT));
        int row = 0;
        row = addRow(section, row, "Source", decisionSource);
        row = addRow(section, row, "Model", decisionModel);
        row = addRow(section, row, "az5_would_spike (noul)", spikeNoul);
        row = addRow(section, row, "scram_action choice", scramChoice);
        row = addRow(section, row, "Choice confidence", choiceConfidence);
        addRow(section, row, "Insertion staged", insertionStaged);
        return section;
    }

    private static int addRow(JPanel panel, int row, String caption, JLabel value) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 8);
        panel.add(captionLabel(caption), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 4);
        panel.add(value, gbc);
        return row + 1;
    }

    private static JLabel captionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(CAPTION_FONT);
        return label;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel(WAITING, SwingConstants.RIGHT);
        label.setOpaque(true);
        label.setBackground(READOUT_BG);
        label.setForeground(READOUT_FG);
        label.setFont(READOUT_FONT);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        return label;
    }

    private void applyGuardStatus() {
        if (Az5Guard.isEnabled()) {
            guardStatus.setText("on — waiting for AZ-5");
        } else {
            guardStatus.setText("off — not consulted");
            decisionSource.setText("not consulted");
            decisionModel.setText(WAITING);
            spikeNoul.setText(WAITING);
            scramChoice.setText(WAITING);
            choiceConfidence.setText(WAITING);
            insertionStaged.setText("no");
        }
    }

    private void clearDecision() {
        if (!Az5Guard.isEnabled()) {
            return;
        }
        decisionSource.setText(WAITING);
        decisionModel.setText(WAITING);
        spikeNoul.setText(WAITING);
        scramChoice.setText(WAITING);
        choiceConfidence.setText(WAITING);
        insertionStaged.setText(WAITING);
    }

    /**
     * Live plant snapshot — the same fields packed for Jev.
     */
    public void showPlantState(Az5PlantState state) {
        double multiplier = DisplacerAccident.fluxMultiplier(state.neutronFluxPercent());
        fluxPercent.setText(formatFixed(state.neutronFluxPercent(), 2));
        fluxMultiplier.setText(formatFixed(multiplier, 3));
        rodsWithdrawn.setText(Integer.toString(state.manualRodsWithdrawn()));
        rodsInWindow.setText(Integer.toString(state.manualRodsInWindow()));
        rodsNotYetClear.setText(Integer.toString(state.manualRodsNotYetClear()));
        rodsTotal.setText(Integer.toString(state.manualRodsTotal()));
        ormEquivalent.setText(formatFixed(state.ormEquivalentRods(), 2));
    }

    /**
     * Guard answer after AZ-5. Visible only once the call returns.
     */
    public void showDecision(Az5Decision decision) {
        guardStatus.setText("on — answered");
        decisionSource.setText(decision.source());
        decisionModel.setText(decision.model());
        spikeNoul.setText(formatFixed(decision.spikeNoul(), 3));
        scramChoice.setText(decision.choice());
        if (Double.isFinite(decision.choiceConfidence())) {
            choiceConfidence.setText(formatFixed(decision.choiceConfidence(), 3));
        } else {
            choiceConfidence.setText(WAITING);
        }
        insertionStaged.setText(decision.stageInsertion() ? "yes" : "no");
    }

    static String formatFixed(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    @Override
    public void updateComponent(PropertyChangeEvent evt) {
        // Plant and decision arrive on the String/Object path.
    }

    @Override
    public void updateComponent(String propertyName, Object newValue) {
        if (propertyName.equals("Az5PlantState") && newValue instanceof Az5PlantState state) {
            showPlantState(state);
            return;
        }
        if (propertyName.equals("Az5Decision") && newValue instanceof Az5Decision decision) {
            showDecision(decision);
        }
    }

    @Override
    public void updateComponent(String propertyName, double newValue) {
        // unused
    }

    @Override
    public void updateComponent(String propertyName, boolean newValue) {
        // unused
    }
}
