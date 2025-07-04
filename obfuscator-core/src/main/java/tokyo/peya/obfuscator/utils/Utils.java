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

package tokyo.peya.obfuscator.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.Localisation;
import tokyo.peya.obfuscator.clazz.ClassReference;
import tokyo.peya.obfuscator.clazz.ClassWrapper;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;

import java.awt.Color;
import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Random;
import java.util.StringJoiner;
import java.util.zip.ZipFile;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_BRIDGE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_STRICT;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.ACC_VOLATILE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.RET;

public class Utils
{
    private static final Random random = new Random();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static ClassNode lookupClass(String name)
    {
        ClassReference ref = ClassReference.of(name);
        ClassWrapper a = JavaObfuscator.getCurrentSession().getClassPath().get(ref);

        if (a != null) return a.classNode;

        return JavaObfuscator.getCurrentSession().getClasses().get(ref);
    }

    public static boolean isWindows()
    {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static MethodNode getMethod(ClassNode cls, String name, String desc, boolean parentClasses)
    {
        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(name) && method.desc.equals(desc))
                return method;
        }

        ClassNode lookup = parentClasses ? lookupClass(cls.superName): null;

        return lookup == null || cls.name.equals("java/lang/Object") ? null: getMethod(lookup, name, desc, true);
    }

    public static FieldNode getField(ClassNode cls, String name)
    {
        for (FieldNode method : cls.fields)
        {
            if (method.name.equals(name))
                return method;
        }
        return null;
    }

    private static boolean isNotInstruction(AbstractInsnNode node)
    {
        return node instanceof LineNumberNode || node instanceof FrameNode || node instanceof LabelNode;
    }

    public static boolean notAbstractOrNative(MethodNode methodNode)
    {
        return !(Modifier.isNative(methodNode.access) || Modifier.isAbstract(methodNode.access));
    }

    public static AbstractInsnNode getNextFollowGoto(AbstractInsnNode node)
    {
        AbstractInsnNode next = node.getNext();
        while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)
            next = next.getNext();

        if (next.getOpcode() == Opcodes.GOTO)
        {
            JumpInsnNode cast = (JumpInsnNode) next;
            next = cast.label;
            while (Utils.isNotInstruction(next))
                next = next.getNext();
        }
        return next;
    }

    public static AbstractInsnNode getNext(AbstractInsnNode node)
    {
        if (node == null)
            return null;

        AbstractInsnNode next = node.getNext();
        if (next == null)
            return null;

        while (Utils.isNotInstruction(next))
        {
            next = next.getNext();

            if (next == null)
                break;
        }
        return next;
    }

    public static AbstractInsnNode getPrevious(AbstractInsnNode node, int amount)
    {
        for (int i = 0; i < amount; i++)
        {
            node = getPrevious(node);
        }
        return node;
    }

    public static AbstractInsnNode getPrevious(AbstractInsnNode node)
    {
        AbstractInsnNode prev = node.getPrevious();
        while (Utils.isNotInstruction(prev))
        {
            prev = prev.getPrevious();
        }
        return prev;
    }

    public static int random(int min, int max)
    {
        return min >= max ? min: random.nextInt(max - min) + min;
    }

    public static <T> T[] concatenate(T[] a, T[] b)
    {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    public static HashMap<LabelNode, LabelNode> generateNewLabelMap(InsnList insnList)
    {
        HashMap<LabelNode, LabelNode> labelNodeHashMap = new HashMap<>();

        for (AbstractInsnNode abstractInsnNode : insnList.toArray())
        {
            if (abstractInsnNode instanceof LabelNode label)
            {

                labelNodeHashMap.put(label, new LabelNode());
            }
        }

        return labelNodeHashMap;
    }

    public static boolean matchMethodNode(MethodInsnNode methodInsnNode, String s)
    {
        return s.equals(methodInsnNode.owner + "." + methodInsnNode.name + ":" + methodInsnNode.desc);
    }

    public static String chooseDirectory(final File currFolder, final Component parent)
    {
        final JFileChooser chooser = new JFileChooser(currFolder);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            return chooser.getSelectedFile().getAbsolutePath();
        return null;
    }

    public static String chooseDirectoryOrFile(final File currFolder, final Component parent)
    {
        final JFileChooser chooser = new JFileChooser(currFolder);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            return chooser.getSelectedFile().getAbsolutePath();
        return null;
    }

    public static String chooseFile(final File currFolder, final Component parent, boolean toSave)
    {
        return chooseFile(currFolder, parent, null, toSave);
    }

    public static String chooseFile(File currFolder, final Component parent, FileFilter filter)
    {
        return chooseFile(currFolder, parent, filter, false);
    }

    public static String chooseFile(File currFolder, final Component parent, FileFilter filter, boolean toSave)
    {
        if (currFolder == null)
            try
            {
                currFolder = new File(Utils.class.getProtectionDomain()
                                                 .getCodeSource()
                                                 .getLocation()
                                                 .toURI()
                                                 .getPath());
            }
            catch (Exception ignored)
            {
            }

        Frame awtFrame = JOptionPane.getFrameForComponent(parent);
        FileDialog dialog = new FileDialog(awtFrame, "Choose a file", toSave ? FileDialog.SAVE: FileDialog.LOAD);
        if (filter != null)
            dialog.setFilenameFilter((dir, name) -> filter.accept(new File(dir, name)));
        if (currFolder != null)
            dialog.setDirectory(currFolder.getAbsolutePath());

        dialog.setVisible(true);
        String file = dialog.getFile();

        return file == null ? null: new File(dialog.getDirectory(), file).getAbsolutePath();
    }

    public static long copy(final InputStream from, final OutputStream to) throws IOException
    {
        byte[] buf = new byte[1024];
        long total = 0L;
        while (true)
        {
            int r = from.read(buf);
            if (r == -1)
            {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public static Color getColor(DeprecationLevel deprecationLevel)
    {
        switch (deprecationLevel)
        {
            case AVAILABLE:
                return null;
            case SOME_DEPRECATION:
                return new Color(255, 127, 80); // coral
            case DEPRECATED:
                return Color.red;
            default:
                return null;
        }
    }

    public static String prettyGson(final JsonObject newObj)
    {
        return gson.toJson(newObj);
    }

    public static String modifierToString(int mod)
    {
        StringJoiner sj = new StringJoiner(" ");

        if ((mod & ACC_BRIDGE) != 0) sj.add("[bridge]");
        if ((mod & ACC_SYNTHETIC) != 0) sj.add("[syntetic]");

        if ((mod & ACC_PUBLIC) != 0) sj.add("public");
        if ((mod & ACC_PROTECTED) != 0) sj.add("protected");
        if ((mod & ACC_PRIVATE) != 0) sj.add("private");

        /* Canonical order */
        if ((mod & ACC_ABSTRACT) != 0) sj.add("abstract");
        if ((mod & ACC_STATIC) != 0) sj.add("static");
        if ((mod & ACC_FINAL) != 0) sj.add("final");
        if ((mod & ACC_TRANSIENT) != 0) sj.add("transient");
        if ((mod & ACC_VOLATILE) != 0) sj.add("volatile");
        if ((mod & ACC_SYNCHRONIZED) != 0) sj.add("synchronized");
        if ((mod & ACC_NATIVE) != 0) sj.add("native");
        if ((mod & ACC_STRICT) != 0) sj.add("strictfp");
        if ((mod & ACC_INTERFACE) != 0) sj.add("interface");

        return sj.toString();
    }

    public static String toIl(int i)
    {
        return Integer.toBinaryString(i).replace('0', 'I').replace('1', 'l');
    }

    /**
     * Converts a string to a new string based on a dictionary. Not actually randomised, but based on the provided
     *
     * @param i value, which will always generate a string unique to that integer.
     *          <p>
     *          It converts an integer into another base based on the length of the dictionary.
     *          For example, if the dictionary was ["hello", "goodbye"] the length of the dictionary is 2, and it will be
     *          converted to its base2 equivalent (binary). But instead of the integer characters they are assigned to
     *          the strings of the dictionary
     * @author cookiedragon234, superblaubeere27
     */
    public static String convertToBase(int i, String str)
    {
        StringBuilder sb = new StringBuilder();

        do
        {
            sb.append(str.charAt(i % str.length()));
            i /= str.length();
        }
        while (i != 0);

        return sb.toString();
    }

    public static String replaceMainClass(String s, ClassReference main)
    {
        StringBuilder sb = new StringBuilder();

        for (String s1 : s.split("\n"))
            if (s1.startsWith("Main-Class"))
                sb.append("Main-Class: ").append(main.getFullQualifiedDotName()).append("\n");
            else
                sb.append(s1).append("\n");

        return sb.toString();
    }

    public static String getInternalName(Type type)
    {
        switch (type.toString())
        {
            case "V":
                return "void";
            case "Z":
                return "boolean";
            case "C":
                return "char";
            case "B":
                return "byte";
            case "S":
                return "short";
            case "I":
                return "int";
            case "F":
                return "float";
            case "J":
                return "long";
            case "D":
                return "double";
            default:
                throw new IllegalArgumentException("Type not known.");
        }
    }

    public static boolean checkZip(File file)
    {
        try (ZipFile ignored = new ZipFile(file))
        {
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static boolean checkClassFile(File file)
    {
        try (FileInputStream fis = new FileInputStream(file))
        {
            byte[] b = new byte[4];
            int i = fis.read(b);
            if (i != 4)
                return false;

            // Magic num: 0xCA FE BA BE
            return (b[0] & 0xFF) == 0xCA
                    && (b[1] & 0xFF) == 0xFE
                    && (b[2] & 0xFF) == 0xBA
                    && (b[3] & 0xFF) == 0xBE;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static String formatTime(long l)
    {
        long hours = l / (1000 * 60 * 60);

        l -= hours * 1000 * 60 * 60;

        long minutes = l / (1000 * 60);

        l -= minutes * 1000 * 60;

        long seconds = l / (1000);

        l -= seconds * 1000;

        StringBuilder sb = new StringBuilder();

        if (hours > 0)
            sb.append(hours).append(Localisation.get("logs.time_unit.hour")).append(" ");
        else if (minutes > 0)
            sb.append(minutes).append(Localisation.get("logs.time_unit.minute")).append(" ");
        else if (seconds > 0 || l > 0)
        {
            String millis = String.valueOf(l / 1000d).replace("0.", "");
            sb.append(seconds).append(".").append(millis).append(Localisation.get("logs.time_unit.second")).append(" ");
        }

        return sb.toString();
    }

    public static Type getType(VarInsnNode abstractInsnNode)
    {
        int offset = getOffset(abstractInsnNode);

        switch (offset)
        {
            case 0:
                return Type.INT_TYPE;
            case LLOAD - ILOAD:
                return Type.LONG_TYPE;
            case FLOAD - ILOAD:
                return Type.FLOAT_TYPE;
            case DLOAD - ILOAD:
                return Type.DOUBLE_TYPE;
            case ALOAD - ILOAD:
                return Type.getType("Ljava/lang/Object;");
        }

        throw new UnsupportedOperationException();
    }

    private static int getOffset(VarInsnNode abstractInsnNode)
    {
        int offset;

        if (abstractInsnNode.getOpcode() >= ISTORE && abstractInsnNode.getOpcode() <= ASTORE)
            offset = abstractInsnNode.getOpcode() - ISTORE;
        else if (abstractInsnNode.getOpcode() >= ILOAD && abstractInsnNode.getOpcode() <= ALOAD)
            offset = abstractInsnNode.getOpcode() - ILOAD;
        else if (abstractInsnNode.getOpcode() == RET)
            throw new UnsupportedOperationException("RET is not supported");
        else
            throw new UnsupportedOperationException();
        return offset;
    }
}
