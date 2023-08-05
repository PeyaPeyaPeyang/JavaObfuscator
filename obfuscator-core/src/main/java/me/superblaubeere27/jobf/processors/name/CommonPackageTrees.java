package me.superblaubeere27.jobf.processors.name;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/*
 * A collection of common packages stored in a tree format
 *
 * @Author cookiedragon234
 */
public class CommonPackageTrees
{
    public static List<Tree> root;

    private static final Tree com;
    private static final Tree org;
    private static final Tree javax;
    private static final Tree net;

    private static final Random random = new Random();

    static
    {
        root = new ArrayList<>();

        com = new Tree("com");

        // Google
        Tree comgoogle = com.add("google");
        Tree comgooglecommon = comgoogle.add("common");
        comgooglecommon.add(Arrays.asList(
                "annotations",
                "base",
                "cache",
                "collect",
                "escape",
                "eventbus",
                "graph",
                "hash",
                "html",
                "io",
                "math",
                "net",
                "primitives",
                "reflect",
                "util",
                "xml"
        ));
        comgoogle.add("java");

        com.add("fasterxml").add("jackson").add("core");

        org = new Tree("org");
        Tree orgapache = org.add("apache");
        Tree orgapachecommons = orgapache.add("commons");
        orgapachecommons.add("codec");
        orgapachecommons.add("io");
        orgapachecommons.add("logging");
        orgapache.add("http");
        org.add("json");
        org.add("reflections");
        org.add("scala");
        org.add("yaml");

        javax = new Tree("javax");
        javax.add("vecmath");

        net = new Tree("net");
        net.add("jodah").add("typetools");


        root.addAll(Arrays.asList(com, org, javax, net));
    }

    public static String getRandomPackage()
    {
        Tree current = null;
        StringBuilder path = new StringBuilder();
        while (true)
        {
            if (current == null)
            {
                current = root.get(random.nextInt(root.size()));
            }

            path.append(current.data).append("/");

            if (random.nextBoolean() || current.leaves.size() <= 0)
            {
                return path.toString();
            }

            int i = random.nextInt(current.leaves.size());
            current = current.leaves.get(i);
        }
    }
}

class Tree
{
    public List<Tree> leaves = new LinkedList<>();
    public Tree parent;
    public String data;

    public Tree(String data)
    {
        this(data, null);
    }

    public Tree(String data, Tree parent)
    {
        this.data = data;
        this.parent = parent;
    }

    public Tree add(String childData)
    {
        return add(new Tree(childData, this));
    }

    public Tree add(Tree child)
    {
        this.leaves.add(child);
        return child;
    }

    public void add(List<String> children)
    {
        for (String s : children)
        {
            add(s);
        }
    }

    public Tree get(Tree child)
    {
        return get(child.data);
    }

    public Tree get(String childData)
    {
        for (Tree leaf : this.leaves)
        {
            if (leaf.data.equals(childData))
                return leaf;
        }
        return null;
    }
}
