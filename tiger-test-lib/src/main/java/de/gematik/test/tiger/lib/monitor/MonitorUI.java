/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib.monitor;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import java.awt.*;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorUI extends JFrame {

    private JLabel message;
    private JButton quitButton;
    private boolean clickedQuitBtn = false;
    private final Pattern showSteps = Pattern.compile(".*TGR (zeige|show) ([\\w|ü|ß]*) (Banner|banner|text|Text) \"(.*)\"");

    public static Optional<MonitorUI> getMonitor()  {
        try {
            return Optional.of(new MonitorUI());
        } catch (HeadlessException hex) {
            log.warn("Unable to start Monitor UI on a headless server!", hex);
            return Optional.empty();
        }

    }
    private MonitorUI() {
        initUI();
        EventQueue.invokeLater(() -> setVisible(true));
    }

    private void initUI() {
        URL url = getClass().getResource("/tiger-500.png");
        quitButton = new JButton("Quit");
        quitButton.addActionListener((event) -> clickedQuitBtn = true);
        message = new JLabel("Starting up ...");
        var pane = getContentPane();
        BorderLayout bl = new BorderLayout(5, 5);
        pane.setLayout(bl);
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);
            icon.setImage(icon.getImage().getScaledInstance(130, 130, Image.SCALE_DEFAULT));
            pane.add(new JLabel(icon), BorderLayout.WEST);
        } else {
            pane.add(new JLabel("TIger"), BorderLayout.WEST);
        }
        pane.add(message, BorderLayout.CENTER);
        setAlwaysOnTop(true);
        setMinimumSize(new Dimension(600, 150));
        setLocationRelativeTo(null);
    }

    public void updateStep(Step step) {
        setMessage(String.join("\n", step.getLines()));
    }

    private void setMessage(String stepText) {
        Matcher m = showSteps.matcher(stepText);
        if (m.find()) {
            // TODO TGR-277 LOWPRIO Julian what else can I do? Only Reflection works here
            try {
                Color color = (Color) Color.class.getDeclaredField(
                    RbelAnsiColors.seekColor(m.group(2)).name().toUpperCase()).get(null);
                if (color == Color.YELLOW || color == Color.ORANGE
                    || color == Color.PINK || color == Color.MAGENTA
                    || color == Color.CYAN|| color == Color.GREEN
                    || color == Color.WHITE) {
                    message.setBackground(Color.BLACK);
                } else {
                    message.setBackground(Color.WHITE);
                }
                message.setForeground(color);
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                message.setForeground(Color.BLACK);
                message.setBackground(Color.WHITE);
            }
            message.setText(m.group(4));
        }
        if (stepText.endsWith("TGR warte auf Abbruch") || stepText.endsWith("TGR wait for user abort")) {
            clickedQuitBtn = false;
            getContentPane().add(quitButton, BorderLayout.EAST);
            pack();
        }
    }

    public void waitForQuit(String appName) {
        message.setForeground(Color.RED);
        message.setBackground(Color.WHITE);
        message.setText("Bitte auf QUIT drücken um " + appName + " zu beenden!");
        await().atMost(24, TimeUnit.HOURS).pollDelay(200, TimeUnit.MILLISECONDS).until(() -> clickedQuitBtn);
    }
}