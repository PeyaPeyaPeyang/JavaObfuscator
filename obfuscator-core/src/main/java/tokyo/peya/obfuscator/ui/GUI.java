/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023      Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.ui;

import com.google.common.base.Throwables;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.Localisation;
import tokyo.peya.obfuscator.configuration.ConfigManager;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.configuration.Value;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.FilePathValue;
import tokyo.peya.obfuscator.configuration.values.StringValue;
import tokyo.peya.obfuscator.templating.Template;
import tokyo.peya.obfuscator.templating.Templates;
import tokyo.peya.obfuscator.utils.JObfFileFilter;
import tokyo.peya.obfuscator.utils.JarFileFilter;
import tokyo.peya.obfuscator.utils.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class GUI extends JFrame
{
    public JTextPane logArea;
    private JTabbedPane tabbedPane1;
    private JPanel panel1;
    private JTextField inputTextField;
    private JButton inputBrowseButton;
    private JTextField outputTextField;
    private JButton outputBrowseButton;
    private JButton obfuscateButton;
    private JButton buildButton;
    private JButton loadButton;
    private JButton saveButton;
    private JCheckBox prettyPrintCheckBox;
    private JTextArea configPanel;
    private JTabbedPane processorOptions;
    private RSyntaxTextArea scriptArea;
    private JList<Template> templates;
    private JButton loadTemplateButton;
    private JCheckBox autoScroll;
    private JList<String> libraries;
    private JButton addButton;
    private JButton removeButton;
    private JSlider threadsSlider;
    private JLabel threadsLabel;
    private JCheckBox verbose;
    private JButton clearLogButton;
    private JButton updatePreviewButton;
    private RSyntaxTextArea originalArea;
    private RSyntaxTextArea obfuscatedArea;
    private JButton pickAnotherClassButton;
    private List<String> libraryList = new ArrayList<>();

    static
    {
        injectUnicodeFont();
    }

    private static void injectUnicodeFont()
    {
        String[] candidates = {
                "Yu Gothic UI",
                "Noto Sans CJK JP",
                "MS Gothic",
                "Arial"
        };

        String selectedFont = Arrays.stream(candidates)
                                    .filter(font -> Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                                                     .getAvailableFontFamilyNames())
                                                          .contains(font))
                                    .findFirst()
                                    .orElse("Arial");

        UIManager.getLookAndFeelDefaults()
                 .put("defaultFont", new Font(selectedFont, Font.PLAIN, 12));
    }

    public GUI()
    {

        $$$setupUI$$$();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(this.panel1);
        setSize(1020, 800);
        setLocationRelativeTo(null);
        setTitle(JavaObfuscator.VERSION);

        this.inputBrowseButton.addActionListener(e -> {
            String file = Utils.chooseFile(null, GUI.this, new JarFileFilter());
            if (file != null)
                this.inputTextField.setText(file);
        });
        this.outputBrowseButton.addActionListener(e -> {
            String file = Utils.chooseFile(null, GUI.this, new JarFileFilter(), true);
            if (file != null)
                this.outputTextField.setText(file);
        });
        this.obfuscateButton.addActionListener(e -> startObfuscator());
        this.buildButton.addActionListener(e -> buildConfig());
        this.saveButton.addActionListener(e -> {
            String name = Utils.chooseFile(null, GUI.this, new JObfFileFilter(), true);
            if (name != null)
            {
                buildConfig();
                try
                {
                    Files.write(new File(name).toPath(), this.configPanel.getText().getBytes(StandardCharsets.UTF_8));
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(
                            GUI.this, e1.toString(), Localisation.get("ui.messages.error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        this.clearLogButton.addActionListener(e -> this.logArea.setText(""));
        this.loadButton.addActionListener(e -> {
            String name = Utils.chooseFile(null, GUI.this, new JObfFileFilter());
            if (name != null)
            {
                buildConfig();
                try
                {
                    Configuration configuration = ConfigManager.loadConfig(Files.readString(Paths.get(name)));
                    this.inputTextField.setText(configuration.getInput());
                    this.outputTextField.setText(configuration.getOutput());
                    this.scriptArea.setText(configuration.getScript());
                    this.libraryList = new ArrayList<>(configuration.getLibraries());
                    updateLibraries();
                    initValues();
                    System.gc();
                }
                catch (Exception e1)
                {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(
                            GUI.this, e1.toString(), Localisation.get("ui.messages.error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        Object[] tmplts = Templates.getTemplates().toArray();
        Template[] templatesArray = Arrays.copyOf(tmplts, tmplts.length, Template[].class);
        this.templates.setListData(templatesArray);
        try
        {
            Theme theme = Theme.load(GUI.class.getResourceAsStream("/theme.xml"));
            theme.apply(this.scriptArea);
            //Preview
            theme.apply(this.originalArea);
            theme.apply(this.obfuscatedArea);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        this.scriptArea.setText("function isRemappingEnabledForClass(node) {\n" +
                                        "    return true;\n" +
                                        "}\n" +
                                        "function isObfuscatorEnabledForClass(node) {\n" +
                                        "    return true;\n" +
                                        "}");
        initValues();
        this.loadTemplateButton.addActionListener(e -> {
            if (this.templates.getSelectedIndex() != -1)
            {
                Configuration config = ConfigManager.loadConfig(this.templates.getSelectedValue().getJson());
                initValues();
                if (config.getScript() != null && !config.getScript().isEmpty())
                {
                    this.scriptArea.setText(config.getScript());
                }
            }
            else
            {
                JOptionPane.showMessageDialog(
                        GUI.this, Localisation.get("ui.messages.error.no_template_selected"),
                        Localisation.get("ui.messages.error"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        this.addButton.addActionListener(e -> {
            String file = Utils.chooseDirectoryOrFile(new File(System.getProperty("java.home")), GUI.this);

            if (file != null)
            {
                File f = new File(file);
                if (f.isDirectory() || Utils.checkZip(f) || Utils.checkClassFile(f))
                {
                    this.libraryList.add(file);
                    updateLibraries();
                }
                else
                {
                    JOptionPane.showMessageDialog(
                            GUI.this, Localisation.get("ui.messages.error.invalid_jvm_file"),
                            Localisation.get("ui.messages.error"), JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        this.removeButton.addActionListener(e -> {
            if (this.libraries.getSelectedIndex() != -1)
            {
                this.libraryList.remove(this.libraries.getSelectedIndex());
                updateLibraries();
            }
            else
            {
                JOptionPane.showMessageDialog(
                        GUI.this, "Maybe you should select a library before removing it :thinking:", "Hmmmm...",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        int cores = Runtime.getRuntime().availableProcessors();


        this.threadsSlider.addChangeListener(
                e -> this.threadsLabel.setText(Integer.toString(this.threadsSlider.getValue())));

        this.threadsSlider.setMinimum(1);
        this.threadsSlider.setMaximum(cores);
        this.threadsSlider.setValue(cores);

        this.updatePreviewButton.addActionListener(e -> {
            try
            {
                if (this.originalArea.getText().isEmpty())
                {
                    JOptionPane.showMessageDialog(
                            GUI.this, Localisation.get("ui.messages.error.no_preview_target"),
                            Localisation.get("ui.messages.error"), JOptionPane.ERROR_MESSAGE
                    );
                }

                byte[] compiled = PreviewGenerator.compile(
                        this.originalArea.getText()
                );
                if (compiled == null)
                {
                    JOptionPane.showMessageDialog(
                            GUI.this, Localisation.get("ui.messages.error.invalid_jvm_file"),
                            Localisation.get("ui.messages.error"), JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                ClassNode compiledCN = PreviewGenerator.toClassNode(compiled);
                ClassNode obfuscatedCN = PreviewGenerator.obfuscate(compiledCN, createConfiguration());
                // Decompiler Crasher が居ると普通にこれもクラッシュするので, 影響部分を取り除く
                obfuscatedCN.methods.stream()
                                    .filter(methodNode -> methodNode.invisibleAnnotations != null)
                                    .forEach(methodNode -> methodNode.invisibleAnnotations.removeIf(
                                            annotationNode -> annotationNode.desc.length() > 100
                                    ));

                String obfuscated = PreviewGenerator.classNodeToCode(obfuscatedCN);
                this.obfuscatedArea.setText(obfuscated);
            }
            catch (Exception e1)
            {
                e1.printStackTrace();
                showExceptionNotification(e1);
            }
        });

        this.pickAnotherClassButton.addActionListener(e -> {
            String input = this.inputTextField.getText();
            if (input == null || input.isEmpty())
            {
                // サンプルコードを表示
                String text = PreviewGenerator.classNodeToCode(PreviewGenerator.DEFAULT_HELLO_WORLD_CLASS);
                this.originalArea.setText(text);
                return;
            }

            Path path = Paths.get(input);
            if (!Files.exists(path))
            {
                JOptionPane.showMessageDialog(
                        GUI.this, Localisation.get("ui.messages.error.invalid_jvm_file"),
                        Localisation.get("ui.messages.error"), JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            ClassNode randomClass = PreviewGenerator.getRandomInputClass(path);
            String text = PreviewGenerator.classNodeToCode(randomClass);
            this.originalArea.setText(text);

            this.updatePreviewButton.doClick();
        });

        this.addJavaBaseLibraries();
        this.setVisible(true);
    }

    public void addJavaBaseLibraries()
    {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isEmpty())
            return;

        Path baseJavaModulePath = Paths.get(javaHome, "jmods", "java.base.jmod");

        if (this.libraryList.contains(baseJavaModulePath.toString()))
        {
            // 既に追加されている場合は何もしない
            return;
        }

        if (Files.exists(baseJavaModulePath))
        {
            this.libraryList.add(baseJavaModulePath.toString());
            this.updateLibraries();
        }
    }

    private void updateLibraries()
    {
        Object[] libraries = this.libraryList.toArray();
        this.libraries.setListData(Arrays.copyOf(libraries, libraries.length, String[].class));
    }

    public void scrollDown()
    {
        if (this.autoScroll.isSelected())
        {
            this.logArea.setCaretPosition(this.logArea.getDocument().getLength());
        }

    }

    private void buildConfig()
    {
        this.configPanel.setText(ConfigManager.generateConfig(
                createConfiguration(),
                                         this.prettyPrintCheckBox.isSelected()
                                 )
        );
    }

    private void showExceptionNotification(Exception e)
    {
        JPanel panel = new JPanel();

        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.setLayout(new BorderLayout(0, 0));

        JTextArea comp = new JTextArea(Throwables.getStackTraceAsString(e));
        comp.setEditable(false);
        JScrollPane scroll = new JScrollPane(
                comp,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        );
        panel.add(scroll);

        JOptionPane.showMessageDialog(
                this,
                panel,
                "ERROR: " + e.getStackTrace()[0].getClassName(),
                JOptionPane.ERROR_MESSAGE
        );

    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$()
    {
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 1, 0, 1), -1, -1));
        tabbedPane1 = new JTabbedPane();
        panel1.add(
                tabbedPane1,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        new Dimension(200, 200),
                        null,
                        0,
                        false
                )
        );
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(5, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.title"), panel2);
        inputTextField = new JTextField();
        inputTextField.setDragEnabled(true);
        panel2.add(
                inputTextField,
                new GridConstraints(
                        0,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        new Dimension(150, -1),
                        null,
                        0,
                        false
                )
        );
        inputBrowseButton = new JButton();
        inputBrowseButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.button.browse"));
        this.$$$loadButtonText$$$(
                inputBrowseButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.button.browse")
        );
        panel2.add(
                inputBrowseButton,
                new GridConstraints(
                        0,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        outputTextField = new JTextField();
        outputTextField.setText("");
        panel2.add(
                outputTextField,
                new GridConstraints(
                        1,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        new Dimension(150, -1),
                        null,
                        0,
                        false
                )
        );
        outputBrowseButton = new JButton();
        outputBrowseButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.button.browse"));
        this.$$$loadButtonText$$$(
                outputBrowseButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.button.browse")
        );
        panel2.add(
                outputBrowseButton,
                new GridConstraints(
                        1,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.input"));
        panel2.add(
                label1,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.output"));
        panel2.add(
                label2,
                new GridConstraints(
                        1,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(
                panel3,
                new GridConstraints(
                        3,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        addButton = new JButton();
        addButton.setActionCommand("Add");
        addButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.libraries.add"));
        this.$$$loadButtonText$$$(
                addButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.libraries.add")
        );
        panel3.add(
                addButton,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final Spacer spacer1 = new Spacer();
        panel3.add(
                spacer1,
                new GridConstraints(
                        2,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_VERTICAL,
                        1,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        removeButton = new JButton();
        removeButton.setActionCommand("Delete");
        removeButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.libraries.remove"));
        this.$$$loadButtonText$$$(
                removeButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.libraries.remove")
        );
        panel3.add(
                removeButton,
                new GridConstraints(
                        1,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        threadsSlider = new JSlider();
        threadsSlider.setMinimum(1);
        threadsSlider.setPaintLabels(true);
        threadsSlider.setPaintTicks(true);
        threadsSlider.setPaintTrack(true);
        threadsSlider.setSnapToTicks(true);
        panel2.add(
                threadsSlider,
                new GridConstraints(
                        2,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.threads"));
        panel2.add(
                label3,
                new GridConstraints(
                        2,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        threadsLabel = new JLabel();
        threadsLabel.setText("");
        panel2.add(
                threadsLabel,
                new GridConstraints(
                        2,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JScrollPane scrollPane1 = new JScrollPane();
        panel2.add(
                scrollPane1,
                new GridConstraints(
                        3,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        libraries = new JList();
        scrollPane1.setViewportView(libraries);
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.io.libraries"));
        panel2.add(
                label4,
                new GridConstraints(
                        3,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_NORTHWEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 1, new Insets(2, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.transformers.title"), panel4);
        processorOptions = new JTabbedPane();
        panel4.add(
                processorOptions,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        new Dimension(200, 200),
                        null,
                        0,
                        false
                )
        );
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        processorOptions.addTab(
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.transformers.untitled"),
                panel5
        );
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(4, 2, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.title"), panel6);
        loadButton = new JButton();
        loadButton.setActionCommand("Load");
        loadButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.button.load"));
        this.$$$loadButtonText$$$(
                loadButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.button.load")
        );
        panel6.add(
                loadButton,
                new GridConstraints(
                        3,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        saveButton = new JButton();
        saveButton.setActionCommand("Save");
        saveButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.button.save"));
        this.$$$loadButtonText$$$(
                saveButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.button.save")
        );
        panel6.add(
                saveButton,
                new GridConstraints(
                        3,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        prettyPrintCheckBox = new JCheckBox();
        prettyPrintCheckBox.setActionCommand("PrettyPrint");
        prettyPrintCheckBox.setLabel(this.$$$getMessageFromBundle$$$(
                "langs/messages",
                "ui.tabs.configuration.pretty_print"
        ));
        this.$$$loadButtonText$$$(
                prettyPrintCheckBox,
                this.$$$getMessageFromBundle$$$(
                        "langs/messages",
                        "ui.tabs.configuration.pretty_print"
                )
        );
        panel6.add(
                prettyPrintCheckBox,
                new GridConstraints(
                        1,
                        0,
                        1,
                        2,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JScrollPane scrollPane2 = new JScrollPane();
        panel6.add(
                scrollPane2,
                new GridConstraints(
                        2,
                        0,
                        1,
                        2,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        configPanel = new JTextArea();
        scrollPane2.setViewportView(configPanel);
        buildButton = new JButton();
        buildButton.setActionCommand("Build");
        buildButton.setLabel(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.configuration.button.build"));
        this.$$$loadButtonText$$$(
                buildButton,
                this.$$$getMessageFromBundle$$$(
                        "langs/messages",
                        "ui.tabs.configuration.button.build"
                )
        );
        panel6.add(
                buildButton,
                new GridConstraints(
                        0,
                        0,
                        1,
                        2,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 1, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.script.title"), panel7);
        final RTextScrollPane rTextScrollPane1 = new RTextScrollPane();
        panel7.add(
                rTextScrollPane1,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        scriptArea = new RSyntaxTextArea();
        scriptArea.setCodeFoldingEnabled(true);
        scriptArea.setColumns(0);
        scriptArea.setRows(0);
        scriptArea.setShowMatchedBracketPopup(false);
        scriptArea.setSyntaxEditingStyle("text/javascript");
        rTextScrollPane1.setViewportView(scriptArea);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(2, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.templates.title"), panel8);
        templates = new JList();
        panel8.add(
                templates,
                new GridConstraints(
                        0,
                        0,
                        1,
                        3,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        null,
                        new Dimension(150, 50),
                        null,
                        0,
                        false
                )
        );
        loadTemplateButton = new JButton();
        loadTemplateButton.setActionCommand("Apply");
        this.$$$loadButtonText$$$(
                loadTemplateButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.templates.button.apply")
        );
        panel8.add(
                loadTemplateButton,
                new GridConstraints(
                        1,
                        0,
                        1,
                        3,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.preview"), panel9);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel9.add(
                panel10,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final Spacer spacer2 = new Spacer();
        panel10.add(
                spacer2,
                new GridConstraints(
                        0,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        1,
                        null,
                        new Dimension(633, 11),
                        null,
                        0,
                        false
                )
        );
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel10.add(
                panel11,
                new GridConstraints(
                        1,
                        0,
                        1,
                        4,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final RTextScrollPane rTextScrollPane2 = new RTextScrollPane();
        rTextScrollPane2.setVerticalScrollBarPolicy(20);
        panel11.add(
                rTextScrollPane2,
                new GridConstraints(
                        0,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        originalArea = new RSyntaxTextArea();
        originalArea.setEditable(true);
        originalArea.setEnabled(true);
        originalArea.setSyntaxEditingStyle("text/java");
        originalArea.setTabSize(2);
        rTextScrollPane2.setViewportView(originalArea);
        final RTextScrollPane rTextScrollPane3 = new RTextScrollPane();
        panel11.add(
                rTextScrollPane3,
                new GridConstraints(
                        0,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        obfuscatedArea = new RSyntaxTextArea();
        obfuscatedArea.setEditable(false);
        obfuscatedArea.setPaintMarkOccurrencesBorder(true);
        obfuscatedArea.setPaintMatchedBracketPair(true);
        obfuscatedArea.setPaintTabLines(true);
        obfuscatedArea.setSyntaxEditingStyle("text/java");
        obfuscatedArea.setTabSize(2);
        obfuscatedArea.setTabsEmulated(true);
        rTextScrollPane3.setViewportView(obfuscatedArea);
        pickAnotherClassButton = new JButton();
        this.$$$loadButtonText$$$(
                pickAnotherClassButton,
                this.$$$getMessageFromBundle$$$(
                        "langs/messages",
                        "ui.tabs.preview.pick_another_class_button"
                )
        );
        panel10.add(
                pickAnotherClassButton,
                new GridConstraints(
                        0,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        updatePreviewButton = new JButton();
        this.$$$loadButtonText$$$(
                updatePreviewButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.preview.update_button")
        );
        panel10.add(
                updatePreviewButton,
                new GridConstraints(
                        0,
                        3,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridLayoutManager(2, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.logs.title"), panel12);
        final JScrollPane scrollPane3 = new JScrollPane();
        panel12.add(
                scrollPane3,
                new GridConstraints(
                        0,
                        0,
                        1,
                        3,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        logArea = new JTextPane();
        logArea.setEditable(false);
        scrollPane3.setViewportView(logArea);
        autoScroll = new JCheckBox();
        autoScroll.setSelected(true);
        this.$$$loadButtonText$$$(
                autoScroll,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.logs.auto_scroll")
        );
        panel12.add(
                autoScroll,
                new GridConstraints(
                        1,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        verbose = new JCheckBox();
        verbose.setSelected(false);
        this.$$$loadButtonText$$$(verbose, this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.logs.verbose"));
        panel12.add(
                verbose,
                new GridConstraints(
                        1,
                        1,
                        1,
                        1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        clearLogButton = new JButton();
        this.$$$loadButtonText$$$(
                clearLogButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.tabs.logs.button.clear")
        );
        panel12.add(
                clearLogButton,
                new GridConstraints(
                        1,
                        2,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        obfuscateButton = new JButton();
        this.$$$loadButtonText$$$(
                obfuscateButton,
                this.$$$getMessageFromBundle$$$("langs/messages", "ui.button.obfuscate")
        );
        panel1.add(
                obfuscateButton,
                new GridConstraints(
                        1,
                        0,
                        1,
                        1,
                        GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null,
                        null,
                        null,
                        0,
                        false
                )
        );
        label1.setLabelFor(inputTextField);
        label2.setLabelFor(outputTextField);
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    private String $$$getMessageFromBundle$$$(String path, String key)
    {
        ResourceBundle bundle;
        try
        {
            Class<?> thisClass = this.getClass();
            if ($$$cachedGetBundleMethod$$$ == null)
            {
                Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
                $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
            }
            bundle = (ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        }
        catch (Exception e)
        {
            bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text)
    {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == '&')
            {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&')
                {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic)
        {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text)
    {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == '&')
            {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&')
                {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic)
        {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {return panel1;}

    private void startObfuscator()
    {
//        impl.loadConfig(config);

        File in = new File(this.inputTextField.getText());

        if (!in.exists())
        {
            JOptionPane.showMessageDialog(
                    this, Localisation.get("ui.messages.error.no_input"),
                    Localisation.get("ui.messages.error"),
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        try
        {
            new Thread(
                    () -> {
                        this.obfuscateButton.setEnabled(false);

                        JavaObfuscator.VERBOSE = this.verbose.isSelected();
                        Configuration config = createConfiguration();

                        boolean succeed = JavaObfuscator.runObfuscator(config);
                        if (!(succeed || JavaObfuscator.getLastException() == null))
                            showExceptionNotification(JavaObfuscator.getLastException());

                        this.obfuscateButton.setEnabled(true);
                    }, "Obfuscator thread"
            ).start();
        }
        catch (Exception e)
        {
            showExceptionNotification(e);
        }
    }

    private Configuration createConfiguration()
    {
        return new Configuration(
                this.libraryList,
                this.inputTextField.getText(),
                this.outputTextField.getText(),
                this.scriptArea.getText(),
                this.threadsSlider.getValue(),
                null
        );
    }

    private void initValues()
    {
        this.processorOptions.removeAll();
        Map<String, ArrayList<Value<?>>> ownerValueMap = ConfigManager.buildValueMap();

//        for (Map.Entry<String, ArrayList<Value<?>>> stringArrayListEntry : ownerValueMap.entrySet()) {
        ownerValueMap.entrySet().stream().sorted(Comparator.comparingInt(entry -> -entry.getValue().size()))
                     .forEach(entry -> {
                         JPanel panel = new JPanel();
                         int rows = 0;

                         for (Value<?> value : entry.getValue())
                         {
                             if (value instanceof BooleanValue)
                             {
                                 BooleanValue booleanValue = (BooleanValue) value;

                                 JCheckBox checkBox = new JCheckBox(
                                         Localisation.get(booleanValue.getLocalisationKey()),
                                         booleanValue.get()
                                 );
                                 checkBox.addActionListener(event -> booleanValue.setValue(checkBox.isSelected()));
                                 panel.add(checkBox);

                                 panel.add(new JLabel(Localisation.has(value.getDescriptionKey()) ? Localisation.get(
                                         value.getDescriptionKey()): ""));

                                 Color c = Utils.getColor(booleanValue.getDeprecation());

                                 if (c != null)
                                     checkBox.setForeground(c);

                                 rows++;
                             }

                             if (value instanceof FilePathValue)
                             {
                                 FilePathValue stringValue = (FilePathValue) value;

                                 JTextField textBox = new JTextField(stringValue.get());

                                 textBox.getDocument().addDocumentListener(new DocumentListener()
                                 {
                                     @Override
                                     public void insertUpdate(DocumentEvent e)
                                     {
                                         stringValue.setValue(textBox.getText());
                                     }

                                     @Override
                                     public void removeUpdate(DocumentEvent e)
                                     {
                                         stringValue.setValue(textBox.getText());
                                     }

                                     @Override
                                     public void changedUpdate(DocumentEvent e)
                                     {
                                         stringValue.setValue(textBox.getText());
                                     }
                                 });

                                 Color c = Utils.getColor(stringValue.getDeprecation());
                                 if (c != null)
                                     textBox.setForeground(c);

                                 panel.add(new JLabel(Localisation.get(stringValue.getLocalisationKey()) + ":"));
                                 panel.add(new JLabel(Localisation.has(stringValue.getDescriptionKey()) ? Localisation.get(
                                         stringValue.getDescriptionKey()): ""));
                                 panel.add(textBox);
                                 JButton browseButton;
                                 panel.add(browseButton = new JButton(Localisation.get("ui.button.browse")));

                                 browseButton.addActionListener(e -> {
                                     String file = Utils.chooseFile(
                                             null, GUI.this, new FileNameExtensionFilter("Text files", "txt"), true);

                                     if (file != null)
                                     {
                                         textBox.setText(file);
                                     }
                                 });

                                 rows += 4;
                             }
                             else if (value instanceof StringValue)
                             {
                                 StringValue stringValue = (StringValue) value;
                                 boolean isMultiLine = stringValue.getTextFieldLines() > 1;

                                 JTextField textBox = new JTextField(stringValue.get());
                                 if (isMultiLine)
                                 {
                                     textBox.setText(Localisation.get("ui.multi_line.click"));
                                     textBox.setHorizontalAlignment(JTextField.CENTER);
                                     textBox.setEditable(false);
                                     textBox.setHighlighter(null);
                                     textBox.setCaretColor(this.getBackground());

                                     textBox.addMouseListener(new MouseAdapter()
                                     {
                                         @Override
                                         public void mouseClicked(MouseEvent e)
                                         {
                                             if (e.getButton() != MouseEvent.BUTTON1)
                                                 return;

                                             MultiLineInput input = new MultiLineInput(GUI.this, stringValue);
                                             input.setVisible(true);
                                         }
                                     });
                                 }
                                 else
                                     textBox.getDocument().addDocumentListener(new DocumentListener()
                                     {
                                         @Override
                                         public void insertUpdate(DocumentEvent e)
                                         {
                                             stringValue.setValue(textBox.getText());
                                         }

                                         @Override
                                         public void removeUpdate(DocumentEvent e)
                                         {
                                             stringValue.setValue(textBox.getText());
                                         }

                                         @Override
                                         public void changedUpdate(DocumentEvent e)
                                         {
                                             stringValue.setValue(textBox.getText());
                                         }
                                     });

                                 Color c = Utils.getColor(stringValue.getDeprecation());

                                 if (c != null)
                                     textBox.setForeground(c);
                                 panel.add(new JLabel(Localisation.get(stringValue.getLocalisationKey()) + ":"));
                                 panel.add(new JLabel(Localisation.has(stringValue.getDescriptionKey()) ? Localisation.get(
                                         stringValue.getDescriptionKey()): ""));
                                 panel.add(textBox);
                                 panel.add(new JLabel(""));

                                 rows += 2;
                             }
                         }
                         panel.setLayout(new GridLayout(rows, 2));
                         JPanel p1 = new JPanel();
                         p1.setLayout(new FlowLayout(FlowLayout.LEFT));
                         p1.add(panel);

                         this.processorOptions.addTab(ValueManager.getLocalisedOwnerName(entry.getKey()), p1);
                     });

//            }

//        }
    }

}
