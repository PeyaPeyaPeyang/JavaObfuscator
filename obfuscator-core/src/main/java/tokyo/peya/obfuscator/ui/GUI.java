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
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.configuration.ConfigManager;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.configuration.Value;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private List<String> libraryList = new ArrayList<>();

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
                        new Configuration(
                                this.libraryList,
                                this.inputTextField.getText(),
                                this.outputTextField.getText(),
                                this.scriptArea.getText(),
                                this.threadsSlider.getValue()
                        ),
                        this.prettyPrintCheckBox.isSelected()
                )
        );
    }

    private static Method $$$cachedGetBundleMethod$$$;

    private void showExceptionDetail(Exception e)
    {
        JPanel panel = new JPanel();

        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.setLayout(new BorderLayout(0, 0));

        JTextArea comp = new JTextArea(Throwables.getStackTraceAsString(e));
        comp.setEditable(false);
        JScrollPane scroll = new JScrollPane(comp, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        panel.add(scroll);

        JOptionPane.showMessageDialog(this, panel, "ERROR encountered at " + e.getStackTrace()[0].getClassName(), JOptionPane.ERROR_MESSAGE);

    }

    public GUI()
    {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(this.panel1);
        setSize(900, 855);
        setLocationRelativeTo(null);
        setTitle(JavaObfuscator.VERSION);

        this.inputBrowseButton.addActionListener(e -> {
            String file = Utils.chooseFile(null, GUI.this, new JarFileFilter());
            if (file != null)
            {
                this.inputTextField.setText(file);
            }
        });
        this.outputBrowseButton.addActionListener(e -> {
            String file = Utils.chooseFile(null, GUI.this, new JarFileFilter(), true);
            if (file != null)
            {
                this.outputTextField.setText(file);
            }
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
                    JOptionPane.showMessageDialog(GUI.this, e1.toString(), "ERROR", JOptionPane.ERROR_MESSAGE);
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
                    Configuration configuration = ConfigManager.loadConfig(new String(Files.readAllBytes(Paths.get(name)), StandardCharsets.UTF_8));
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
                    JOptionPane.showMessageDialog(GUI.this, e1.toString(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        Object[] tmplts = Templates.getTemplates().toArray();
        Template[] templatesArray = Arrays.copyOf(tmplts, tmplts.length, Template[].class);
        this.templates.setListData(templatesArray);
        try
        {
            Theme.load(GUI.class.getResourceAsStream("/theme.xml")).apply(this.scriptArea);
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
                JOptionPane.showMessageDialog(GUI.this, "Maybe you should select a template before applying it :thinking:", "Hmmmm...", JOptionPane.ERROR_MESSAGE);
            }
        });

        this.addButton.addActionListener(e -> {
            String file = Utils.chooseDirectoryOrFile(new File(System.getProperty("java.home")), GUI.this);

            if (file != null)
            {
                if (new File(file).isDirectory() || Utils.checkZip(file))
                {
                    this.libraryList.add(file);
                    updateLibraries();
                }
                else
                {
                    JOptionPane.showMessageDialog(GUI.this, "This file isn't a valid file. Allowed: ZIP-Files, Directories", "ERROR", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(GUI.this, "Maybe you should select a library before removing it :thinking:", "Hmmmm...", JOptionPane.ERROR_MESSAGE);
            }
        });

        int cores = Runtime.getRuntime().availableProcessors();


        this.threadsSlider.addChangeListener(e -> this.threadsLabel.setText(Integer.toString(this.threadsSlider.getValue())));

        this.threadsSlider.setMinimum(1);
        this.threadsSlider.setMaximum(cores);
        this.threadsSlider.setValue(cores);


        setVisible(true);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
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
        panel1.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(6, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("strings", "input.output"), panel2);
        inputTextField = new JTextField();
        panel2.add(inputTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        inputBrowseButton = new JButton();
        this.$$$loadButtonText$$$(inputBrowseButton, this.$$$getMessageFromBundle$$$("strings", "browse"));
        panel2.add(inputBrowseButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        outputTextField = new JTextField();
        panel2.add(outputTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        outputBrowseButton = new JButton();
        this.$$$loadButtonText$$$(outputBrowseButton, this.$$$getMessageFromBundle$$$("strings", "browse"));
        panel2.add(outputBrowseButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("strings", "input"));
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("strings", "output"));
        panel2.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Libraries:");
        panel2.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        addButton = new JButton();
        addButton.setText("Add");
        panel3.add(addButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel3.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        removeButton = new JButton();
        removeButton.setText("Remove");
        panel3.add(removeButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        threadsSlider = new JSlider();
        threadsSlider.setMinimum(1);
        threadsSlider.setPaintLabels(true);
        threadsSlider.setPaintTicks(true);
        threadsSlider.setPaintTrack(true);
        threadsSlider.setSnapToTicks(true);
        panel2.add(threadsSlider, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Threads");
        panel2.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        threadsLabel = new JLabel();
        threadsLabel.setText("");
        panel2.add(threadsLabel, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel2.add(scrollPane1, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        libraries = new JList();
        scrollPane1.setViewportView(libraries);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 1, new Insets(2, 5, 1, 5), -1, -1));
        tabbedPane1.addTab("Transformers", panel4);
        processorOptions = new JTabbedPane();
        panel4.add(processorOptions, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        processorOptions.addTab("Untitled", panel5);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(4, 2, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab("Config", panel6);
        buildButton = new JButton();
        buildButton.setText("Build");
        panel6.add(buildButton, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loadButton = new JButton();
        loadButton.setText("Load");
        panel6.add(loadButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveButton = new JButton();
        saveButton.setText("Save");
        panel6.add(saveButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        prettyPrintCheckBox = new JCheckBox();
        prettyPrintCheckBox.setText("PrettyPrint");
        panel6.add(prettyPrintCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel6.add(scrollPane2, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        configPanel = new JTextArea();
        scrollPane2.setViewportView(configPanel);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 1, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab("Script", panel7);
        final RTextScrollPane rTextScrollPane1 = new RTextScrollPane();
        panel7.add(rTextScrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        scriptArea = new RSyntaxTextArea();
        scriptArea.setCodeFoldingEnabled(true);
        scriptArea.setColumns(0);
        scriptArea.setRows(0);
        scriptArea.setShowMatchedBracketPopup(false);
        scriptArea.setSyntaxEditingStyle("text/javascript");
        rTextScrollPane1.setViewportView(scriptArea);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(2, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab("Templates", panel8);
        templates = new JList();
        panel8.add(templates, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        loadTemplateButton = new JButton();
        loadTemplateButton.setText("Apply");
        panel8.add(loadTemplateButton, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(2, 3, new Insets(5, 5, 1, 5), -1, -1));
        tabbedPane1.addTab(this.$$$getMessageFromBundle$$$("strings", "log"), panel9);
        final JScrollPane scrollPane3 = new JScrollPane();
        panel9.add(scrollPane3, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        logArea = new JTextPane();
        logArea.setEditable(false);
        scrollPane3.setViewportView(logArea);
        autoScroll = new JCheckBox();
        autoScroll.setSelected(true);
        autoScroll.setText("AutoScroll");
        panel9.add(autoScroll, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        verbose = new JCheckBox();
        verbose.setSelected(false);
        verbose.setText("Verbose");
        panel9.add(verbose, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        clearLogButton = new JButton();
        clearLogButton.setText("Clear");
        panel9.add(clearLogButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        obfuscateButton = new JButton();
        this.$$$loadButtonText$$$(obfuscateButton, this.$$$getMessageFromBundle$$$("strings", "obfuscate"));
        panel1.add(obfuscateButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label1.setLabelFor(inputTextField);
        label2.setLabelFor(outputTextField);
    }

    private void startObfuscator()
    {
//        impl.loadConfig(config);

        File in = new File(this.inputTextField.getText());

        if (!in.exists())
        {
            JOptionPane.showMessageDialog(this, "Input file doesn't exist!", "ERROR", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try
        {
            new Thread(() -> {
                this.obfuscateButton.setEnabled(false);

                JavaObfuscator.VERBOSE = this.verbose.isSelected();
                Configuration config = new Configuration(
                        this.libraryList,
                        this.inputTextField.getText(),
                        this.outputTextField.getText(),
                        this.scriptArea.getText(),
                        this.threadsSlider.getValue()
                );

                boolean succeed = JavaObfuscator.runObfuscator(config);
                if (!(succeed || JavaObfuscator.getLastException() == null))
                    showExceptionDetail(JavaObfuscator.getLastException());

                this.obfuscateButton.setEnabled(true);
            }, "Obfuscator thread").start();
        }
        catch (Exception e)
        {
            showExceptionDetail(e);
        }
    }

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
    public JComponent $$$getRootComponent$$$()
    {
        return panel1;
    }

    private void initValues()
    {
        this.processorOptions.removeAll();
        Map<String, ArrayList<Value<?>>> ownerValueMap = ConfigManager.buildValueMap();

//        for (Map.Entry<String, ArrayList<Value<?>>> stringArrayListEntry : ownerValueMap.entrySet()) {
        ownerValueMap.entrySet().stream().sorted(Comparator.comparingInt(entry -> -entry.getValue().size())).forEach(entry -> {
            JPanel panel = new JPanel();
            int rows = 0;

            for (Value<?> value : entry.getValue())
            {
                if (value instanceof BooleanValue)
                {
                    BooleanValue booleanValue = (BooleanValue) value;

                    JCheckBox checkBox = new JCheckBox(value.getName(), booleanValue.get());
                    checkBox.addActionListener(event -> booleanValue.setValue(checkBox.isSelected()));
                    panel.add(checkBox);

                    panel.add(new JLabel(booleanValue.getDescription() == null ? "": booleanValue.getDescription()));

                    Color c = Utils.getColor(booleanValue.getDeprecation());

                    if (c != null)
                    {
                        checkBox.setForeground(c);
                    }

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
                    {
                        textBox.setForeground(c);
                    }
                    panel.add(new JLabel(stringValue.getName() + ":"));
                    panel.add(new JLabel(stringValue.getDescription() == null ? "": stringValue.getDescription()));
                    panel.add(textBox);
                    JButton browseButton;
                    panel.add(browseButton = new JButton("Browse"));

                    browseButton.addActionListener(e -> {
                        String file = Utils.chooseFile(null, GUI.this, new FileNameExtensionFilter("Text files", "txt"), true);

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

                    if (stringValue.getTextFieldLines() > 1)
                    {
                        JTextField textBox = new JTextField("> Click here to edit... <");
                        textBox.setHorizontalAlignment(JTextField.CENTER);
                        textBox.setEditable(false);
                        textBox.setHighlighter(null);
                        textBox.setBorder(null);
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

                        Color c = Utils.getColor(stringValue.getDeprecation());

                        if (c != null)
                            textBox.setForeground(c);

                        panel.add(new JLabel(stringValue.getName() + ":"));
                        panel.add(new JLabel(stringValue.getDescription() == null ? "": stringValue.getDescription()));
                        panel.add(new JScrollPane(textBox));
                        panel.add(new JLabel(""));
                    }
                    else
                    {
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
                        panel.add(new JLabel(stringValue.getName() + ":"));
                        panel.add(new JLabel(stringValue.getDescription() == null ? "": stringValue.getDescription()));
                        panel.add(textBox);
                        panel.add(new JLabel(""));
                    }

                    rows += 4;
                }
            }
            panel.setLayout(new GridLayout(rows, 2));
            JPanel p1 = new JPanel();
            p1.setLayout(new FlowLayout(FlowLayout.LEFT));
            p1.add(panel);

            this.processorOptions.addTab(entry.getKey(), p1);
        });

//            }

//        }
    }

}
