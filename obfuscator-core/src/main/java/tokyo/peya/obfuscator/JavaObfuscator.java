/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023-2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.io.ByteStreams;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import tokyo.peya.obfuscator.configuration.ConfigManager;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.processor.Processors;
import tokyo.peya.obfuscator.utils.ConsoleUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class JavaObfuscator
{
    public static final String SHORT_VERSION =
            (JavaObfuscator.class.getPackage().getImplementationVersion() == null
                    ? "DEV": "v" + JavaObfuscator.class.getPackage().getImplementationVersion())
                    + " by superblaubeere27 & Peyang";
    public static final String VERSION = "Java Obfuscator " + SHORT_VERSION;

    public static boolean VERBOSE;
    @Getter
    private static Obfuscator currentSession;
    @Setter
    @Getter
    private static Exception lastException;


    public static void initialise()
    {
        if (JavaObfuscator.class.getPackage().getImplementationVersion() == null)  // デバッガの場合
            VERBOSE = true;

        try
        {
            Class.forName(Obfuscator.class.getCanonicalName());
        }
        catch (ClassNotFoundException e)
        {
            log.error("Obfuscator class not found! Please make sure you are running the obfuscator jar file.");
            System.exit(1);
        }
        Processors.loadProcessors();
    }

    public static void main(String[] args) throws Exception
    {
        initialise();

        try
        {
            boolean embedded = false;
            printHeader(embedded);

            runObfuscatorWithArguments(args, embedded);
        }
        catch (OptionException e)
        {
            log.error(e.getMessage() + " (Tip: try --help and even if you specified a config you have to specify an input and output jar)");
        }
        finally
        {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.stop();
        }
    }

    private static void runObfuscatorWithArguments(String[] args, boolean embedded) throws  Exception
    {
        OptionParser parser = createParser();
        OptionSet options = parser.parse(args);

        if (options.has("help"))
        {
            System.out.println(VERSION);
            parser.printHelpOn(System.out);
            return;
        }
        else if (options.has("version"))
        {
            System.out.println(VERSION);
            return;
        }

        if (options.has("verbose"))
            VERBOSE = true;

        String jarIn = (String) options.valueOf("jarIn");
        String jarOut = (String) options.valueOf("jarOut");
        File configPath = options.has("config") ? (File) options.valueOf("config"): null;

        String scriptContent = null;
        if (options.has("scriptFile"))
            scriptContent = Files.readString(((File) options.valueOf("scriptFile")).toPath());

        int threads = Math.max(1, (Integer) options.valueOf("threads"));
        File mapping = options.has("mapping") ? (File) options.valueOf("mapping"): null;

        List<String> libraries = new ArrayList<>();

        if (options.has("cp"))
            for (Object cp : options.valuesOf("cp"))
                libraries.add(cp.toString());

        runObfuscator(jarIn, jarOut, configPath, libraries, embedded, scriptContent, threads, mapping);
    }

    private static OptionParser createParser()
    {
        OptionParser parser = new OptionParser();
        parser.accepts("jarIn").withRequiredArg().required();
        parser.accepts("jarOut").withRequiredArg();
        parser.accepts("config").withOptionalArg().ofType(File.class);
        parser.accepts("cp").withOptionalArg().describedAs("ClassPath").ofType(File.class);
        parser.accepts("scriptFile")
              .withOptionalArg()
              .describedAs("[Not documented] JS script file")
              .ofType(File.class);
        parser.accepts("threads")
              .withOptionalArg()
              .ofType(Integer.class)
              .defaultsTo(Runtime.getRuntime().availableProcessors())
              .describedAs(
                      "Thread count; Please don't use more threads than you have cores. It might hang up your system");
        parser.accepts("mapping").withOptionalArg().ofType(File.class).describedAs("Mapping file");
        parser.accepts("verbose").withOptionalArg();
        parser.accepts("help").forHelp();
        parser.accepts("version").forHelp();

        return parser;
    }

    private static void printHeader(boolean embedded)
    {

        log.info("\n" +
                         "        _      __                     _             \n" +
                         "       | |    / _|                   | |            \n" +
                         "   ___ | |__ | |_ _   _ ___  ___ __ _| |_ ___  _ __ \n" +
                         "  / _ \\| '_ \\|  _| | | / __|/ __/ _` | __/ _ \\| '__|\n" +
                         " | (_) | |_) | | | |_| \\__ \\ (_| (_| | || (_) | |   \n" +
                         "  \\___/|_.__/|_|  \\__,_|___/\\___\\__,_|\\__\\___/|_|   \n" +
                         "   " + SHORT_VERSION + (embedded ? " (EMBEDDED)": "") +
                         "\n\n");
    }

    public static boolean runObfuscator(String jarIn,
                                        String jarOut,
                                        File configPath,
                                        List<String> libraries,
                                        boolean embedded,
                                        String scriptContent,
                                        int threads,
                                        File mapping) throws IOException, InterruptedException
    {
        log.info("\n" + ConsoleUtils.formatBox(
                "Configuration", false, Arrays.asList(
                        "Input:      " + jarIn,
                        "Output:     " + jarOut,
                        "Config:     " + (configPath != null ? configPath.getPath(): ""),
                        "Script?:     " + (scriptContent != null ? "Yes": "No")
                )
        ));

        Configuration config = new Configuration(
                libraries,
                jarIn,
                jarOut,
                scriptContent,
                threads,
                mapping != null ? mapping.getPath(): null
        );

        if (configPath != null)
        {
            if (!configPath.exists())
            {
                log.error("Config file specified but not found!");
                return false;
            }

            config = ConfigManager.loadConfig(new String(
                    ByteStreams.toByteArray(Files.newInputStream(configPath.toPath())),
                    StandardCharsets.UTF_8
            ));
        }
        else
        {
            log.warn("\n" + ConsoleUtils.formatBox(
                    "No config file", true, Arrays.asList(
                            "You didn't specify a configuration, so the ",
                            "obfuscator is using the default configuration.",
                            " ",
                            "This might cause the output jar to be invalid.",
                            "If you want to create a config, please start the",
                            "obfuscator in GUI Mode (run it without cli args).",
                            "",
                            !embedded ? "The program will resume in 2 sec": "Continue..."
                    )
            ) + "\n");
            if (!embedded)
                Thread.sleep(2000);
        }

        return runObfuscator(jarIn, jarOut, config, libraries, scriptContent, threads, mapping);
    }

    public static boolean runObfuscator(String jarIn,
                                        String jarOut,
                                        Configuration config,
                                        List<String> libraries,
                                        String scriptContent,
                                        int threads,
                                        File mapping)
    {
        if (StringUtils.isEmpty(config.getInput()))
            config.setInput(jarIn);
        if (StringUtils.isEmpty(config.getOutput()))
            config.setOutput(jarOut);
        if (config.getNThreads() == -1)
            config.setNThreads(threads);
        if (config.getMapping() == null)
            config.setMapping(mapping != null ? mapping.getPath(): null);

        config.getLibraries().addAll(libraries);

        if (!(scriptContent == null || scriptContent.isEmpty()))
            config.setScript(scriptContent);

        return runObfuscator(config, null);
    }

    @SneakyThrows(InterruptedException.class)
    public static boolean runObfuscator(Configuration config, Consumer<? super Obfuscator> onObfuscatorCreateD)
    {
        syncLogger();
        int threads = config.getNThreads();
        if (threads > Runtime.getRuntime().availableProcessors())
        {
            log.warn("\n" + ConsoleUtils.formatBox(
                    "WARNING", true, Arrays.asList(
                            "You selected more threads than your cpu has cores.",
                            "",
                            "I would strongly advise against it because",
                            "it WILL make the obfuscation slower and also",
                            "might hang up your system. " + threads + " threads > " + Runtime.getRuntime()
                                                                                             .availableProcessors() + " cores",
                            "",
                            "The program will resume in 10s. Please think about your decision"
                    )
            ) + "\n");
            Thread.sleep(10000);
        }

        boolean succeed;
        try
        {
            lastException = null;
            Obfuscator obfuscator = JavaObfuscator.currentSession = new Obfuscator(config);
            if (onObfuscatorCreateD != null)
                onObfuscatorCreateD.accept(obfuscator);
            obfuscator.process();
            succeed = true;
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
            lastException = e;
            succeed = false;
        }

        JavaObfuscator.currentSession = null;
        return succeed;
    }

    private static void syncLogger()
    {
        Level logLevel = Level.INFO;
        if (JavaObfuscator.VERBOSE)
            logLevel = Level.DEBUG;

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(logLevel);
    }

    public static ClassNode obfuscateClass(ClassNode classNode, Configuration config) throws IOException
    {
        log.debug("Obfuscating one class: " + classNode.name);
        Obfuscator obfuscator = JavaObfuscator.currentSession = new Obfuscator(config);
        ClassNode obfuscated = obfuscator.processClass(classNode);
        JavaObfuscator.currentSession = null;
        log.debug("DONE!");

        return obfuscated;
    }
}
