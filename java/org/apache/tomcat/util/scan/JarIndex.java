package org.apache.tomcat.util.scan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;

public class JarIndex {
    private final HashMap<String,LinkedList<String>> indexMap;

    public JarIndex(InputStream is) throws IOException {
        indexMap = new HashMap<>();
        read(is);
    }

    public LinkedList<String> get(String fileName) {
        LinkedList<String> jarFiles;
        if ((jarFiles = indexMap.get(fileName)) == null) {
            int pos;
            if((pos = fileName.lastIndexOf('/')) != -1) {
                jarFiles = indexMap.get(fileName.substring(0, pos));
            }
        }
        return jarFiles;
    }

    private void read(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader
                (new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        String currentJar = null;

        while((line = br.readLine()) != null && !line.endsWith(".jar"));

        for(;line != null; line = br.readLine()) {
            if (line.length() == 0)
                continue;

            if (line.endsWith(".jar")) {
                currentJar = line;
            } else {
                addToList(line,currentJar,indexMap);
            }
        }
    }

    private void addToList(String key, String value,
                           HashMap<String, LinkedList<String>> t) {
        LinkedList<String> list = t.get(key);
        if (list == null) {
            list = new LinkedList<>();
            list.add(value);
            t.put(key, list);
        } else if (!list.contains(value)) {
            list.add(value);
        }
    }
}
