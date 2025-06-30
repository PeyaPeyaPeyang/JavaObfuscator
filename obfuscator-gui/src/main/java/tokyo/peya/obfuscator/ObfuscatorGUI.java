/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.formdev.flatlaf.FlatDarculaLaf;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import tokyo.peya.obfuscator.templating.Templates;
import tokyo.peya.obfuscator.ui.GUI;
import tokyo.peya.obfuscator.utils.Utils;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

@Slf4j(topic = "GUI-Bootstrap")
public class ObfuscatorGUI
{
    private static GUI gui;

    public static JTextPane getGui()
    {
        return gui != null ? gui.logArea: null;
    }

    private static void injectLogback()
    {
        try (InputStream is = ObfuscatorGUI.class.getClassLoader().getResourceAsStream("logback-gui.xml"))
        {
            if (is == null)
                throw new IllegalStateException("logback-gui.xml not found");

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(is);


        }
        catch (IOException | JoranException e)
        {
            throw new IllegalStateException("Failed to configure logback for GUI", e);
        }
    }

    public static void main(String[] args)
    {
        injectLogback();
        JavaObfuscator.initialise();

        if (GraphicsEnvironment.isHeadless())
        {
            log.info("This build is a headless build, so GUI is not available");
            return;
        }

        if (GraphicsEnvironment.isHeadless())
        {
            log.info("This build is a headless build, so GUI is not available");
            return;
        }

        log.info("Starting in GUI Mode");

        try
        {
            if (Utils.isWindows())
                FlatDarculaLaf.setup();
            else
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e1)
        {
            try
            {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception e2)
            {
                log.error("Failed to set LookAndFeel", e2);
            }
        }

        Templates.loadTemplates();

        gui = new GUI();

    }
}
