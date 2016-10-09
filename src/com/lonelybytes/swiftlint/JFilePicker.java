package com.lonelybytes.swiftlint;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JButton;
import java.awt.*;
import java.awt.event.ActionEvent;

class JFilePicker extends JPanel {
    private JTextField textField;
    private JFileChooser fileChooser;

    JFilePicker(String textFieldLabel, String buttonLabel) {
        fileChooser = new JFileChooser();

        setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

        JLabel label = new JLabel(textFieldLabel);

        textField = new JTextField(30);
        JButton button = new JButton(buttonLabel);
        button.addActionListener(this::buttonActionPerformed);

        add(label);
        add(textField);
        add(button);
    }

    JTextField getTextField() {
        return textField;
    }

    private void buttonActionPerformed(ActionEvent evt) {
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            textField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
}