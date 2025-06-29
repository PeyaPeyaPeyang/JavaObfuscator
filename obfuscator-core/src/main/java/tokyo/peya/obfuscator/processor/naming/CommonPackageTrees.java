package tokyo.peya.obfuscator.processor.naming;

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
    private static final Random RANDOM = new Random();
    private static final List<Tree> TREE_ROOTS = new ArrayList<>();

    static {
        Tree treeCom = getOrCreateRoot("com");
        treeCom.addPath("google").add(Arrays.asList(
                "common.annotations.beta",
                "common.base.internal",
                "common.cache.local",
                "common.collect.immutable",
                "common.eventbus.publisher",
                "common.hash.bloom",
                "common.html.parser",
                "common.math.stats",
                "common.math.geometry",
                "common.net.http",
                "common.util.logging",
                "common.xml.serializer",
                "common.xml.parser"
        ));
        treeCom.addPath("mysql.cj").add(Arrays.asList(
                "jdbc",
                "protocol",
                "exceptions",
                "util",
                "conf",
                "log",
                "authentication",
                "xdevapi",
                "xdevapi.impl",
                "performance"
        ));
        treeCom.addPath("fasterxml.jackson").add(Arrays.asList(
                "annotation.introspect",
                "core.json",
                "core.yaml",
                "databind.deser"
        ));

        Tree treeOrg = getOrCreateRoot("org");
        treeOrg.addPath("apache.commons").add(Arrays.asList(
                "codec.binary",
                "collections4.map",
                "configuration2.env",
                "io.comparator",
                "math3.stat",
                "text.similarity",
                "text.translate"
        ));
        treeOrg.addPath("springframework").add(Arrays.asList(
                "beans.factory.support",
                "context.annotation",
                "core.io.support",
                "web.servlet.handler",
                "web.servlet.view",
                "web.reactive.function.server",
                "data.redis.connection",
                "boot.autoconfigure.jdbc"
        ));
        treeOrg.addPath("mockito").add(Arrays.asList(
                "core",
                "junit.jupiter",
                "invocation",
                "mock",
                "stubbing"
        ));
        treeOrg.addPath("sqlite").add(Arrays.asList(
                "jdbc.core.connection",
                "jdbc.core.statement",
                "jdbc.core.resultset",
                "jdbc.native.api",
                "jdbc.native.buffer",
                "sqlite.core.auth",
                "sqlite.sqlite3.platform.linux",
                "sqlite.sqlite3.platform.windows",
                "sqlite.sqlite3.platform.macos"
        ));


        Tree treeNet = getOrCreateRoot("net");
        treeNet.addPath("minecraft").add(Arrays.asList(
                "client.render.gui",
                "client.sound",
                "server.command",
                "network.protocol",
                "block.state",
                "block.material",
                "item.enchantment"
        ));
    }

    private static Tree getOrCreateRoot(String name)
    {
        // あれば返す
        for (Tree root : TREE_ROOTS)
            if (root.data.equals(name))
                return root;

        Tree newRoot = new Tree(name, null);
        TREE_ROOTS.add(newRoot);
        return newRoot;
    }

    public static String getRandomPackage()
    {
        Tree current = null;
        StringBuilder path = new StringBuilder();
        int depth = 0;
        final int MAX_DEPTH = 10;  // 最大深さ制限（好きに調整してOK）

        while (true)
        {
            if (current == null)
                current = TREE_ROOTS.get(RANDOM.nextInt(TREE_ROOTS.size()));

            path.append(current.data).append("/");

            depth++;

            if (current.leaves.isEmpty() || depth >= MAX_DEPTH)
                return path.toString();

            double stopProbability = 0.3;
            if (RANDOM.nextDouble() < stopProbability)
                return path.toString();

            int i = RANDOM.nextInt(current.leaves.size());
            current = current.leaves.get(i);
        }
    }

    public static boolean isCommonPackage(String name)
    {
        List<String> parts = Arrays.asList(name.split("\\."));
        if (parts.size() < 2)
            return false;

        Tree current = null;
        for (String part : parts)
        {
            if (current != null)
                current = current.get(part);
            else
                for (Tree root : CommonPackageTrees.TREE_ROOTS)
                {
                    if (root.data.equals(part))
                    {
                        current = root;
                        break;
                    }
                }
        }

        return current != null && !current.leaves.isEmpty();
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

    public Tree add(Tree child)
    {
        this.leaves.add(child);
        return child;
    }

    public void add(List<String> children)
    {
        for (String s : children)
            addPath(s);
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


    public Tree addPath(String dottedPath)
    {
        String[] parts = dottedPath.split("\\.");
        if (parts.length == 0)
            return this;

        Tree current = this;
        for (String part : parts)
        {
            Tree next = current.get(part);
            if (next == null)
            {
                next = new Tree(part, current);
                current.add(next);
            }
            current = next;
        }

        return current;
    }

}
