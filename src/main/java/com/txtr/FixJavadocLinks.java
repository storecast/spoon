package com.txtr;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class FixJavadocLinks {

    private static TreeMap<String, Map<String, Map<String, String>>> info = UpdateInterface.readInfo();

    private static final String[] SEARCH;
    private static final String[] REPLACE;

    static {
        Map<String, String> replaceMap = new LinkedHashMap<>();
        for (Entry<String, Map<String, Map<String, String>>> entry : info.entrySet()) {
            String authMethod = Iterables.getOnlyElement(entry.getValue().keySet());

            addReplacement(replaceMap, authMethod, "{@link I" + StringUtils.replace(entry.getKey(), ".", "#"));
            addReplacement(replaceMap, authMethod, "{@link #" + StringUtils.substringAfter(entry.getKey(), "."));
            addReplacement(replaceMap, authMethod, "see I" + StringUtils.replace(entry.getKey(), ".", "#"));
            addReplacement(replaceMap, authMethod, "see #" + StringUtils.substringAfter(entry.getKey(), "."));
            addReplacement(replaceMap, authMethod, "{@link  I" + StringUtils.replace(entry.getKey(), ".", "#"));
            addReplacement(replaceMap, authMethod, "{@link  #" + StringUtils.substringAfter(entry.getKey(), "."));
            addReplacement(replaceMap, authMethod, "see  I" + StringUtils.replace(entry.getKey(), ".", "#"));
            addReplacement(replaceMap, authMethod, "see  #" + StringUtils.substringAfter(entry.getKey(), "."));
        }

        SEARCH = replaceMap.keySet().toArray(new String[replaceMap.size()]);
        REPLACE = replaceMap.values().toArray(new String[replaceMap.size()]);
    }

    private static void addReplacement(Map<String, String> replaceMap, String authMethod, String method) {
        replaceMap.put(method + "(String", method + "(" + getToken(authMethod));
    }

    private static String getToken(String type) {
        switch (type) {
            case "verifyAnonymousUser":
                return "AnonymousUserToken";
            case "verifyDummyContext":
                return "DummyToken";
            case "verifyKnownUser":
                return "KnownUserToken";
        }
        return "String";
    }

    public static void main(String[] args) throws IOException {
//        for (File file : FileUtils.listFiles(new File("/home/gregor/source/dev/api/com.bookpac.server.wsapi/src/main/java"), new String[]{"java"}, true)) {
        for (File file : FileUtils.listFiles(new File("/home/gregor/source/dev/server/com.bookpac.server.json/src/main/resources"), new String[]{"html"}, true)) {
            System.out.println(file);

            List<String> list = FileUtils.readLines(file, Charsets.UTF_8);
            FileUtils.writeLines(file, Charsets.UTF_8.toString(), Lists.transform(list, new Function<String, Object>() {
                @Nullable
                @Override
                public Object apply(@Nullable String input) {
                    return StringUtils.replaceEach(input, SEARCH, REPLACE);
                }
            }), "\n");
        }
    }

}
