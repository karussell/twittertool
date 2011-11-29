/**
 * Copyright (C) 2010 Peter Karich <info@jetsli.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetsli.twitter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Put here what you want in the read only configuration file
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Configuration {

    private Properties prop;
    private static final String CONFIG_FILE = "tt.config.file";
    private String file;

    public Configuration() {
        file = getFileUnderHome("config.properties");
        if (System.getProperty(CONFIG_FILE) != null)
            file = System.getProperty(CONFIG_FILE);

        try {
            prop = new Properties();
            prop.load(new InputStreamReader(new FileInputStream(file), "UTF8"));
        } catch (IOException ex) {
            throw new IllegalArgumentException("TwitterTool needs a config file under:" + file);
        }
    }

    public static String getFileUnderHome(String str) {
//        char c = File.separatorChar;
//        String appHome = System.getProperty("user.home") + c + ".twittertool";
//        File f = new File(appHome);
//        if (!f.exists())
//            f.mkdir();
//        return appHome + c + str;
        return str;
    }

    public String getUserBlacklist() {
        String key = "tt.usearch.blacklist";
        return get(key);
    }

    public Credits getTwitterSearchCredits() {
        String key = "tt.twitter4j.main.";
        return new Credits(get(key + "token"), get(key + "tokenSecret"),
                get(key + "consumerKey"), get(key + "consumerSecret"));
    }

    public boolean isStreamEnabled() {
        String key = get("twitter.stream.enable");
        if (key == null)
            return true;
        return Boolean.parseBoolean(key);
    }


    protected String get(String key) {
        return get(key, false);
    }

    protected String get(String key, boolean requiredProperty) {
        // system values are more important!
        String val = System.getProperty(key);
        if (val == null)
            val = prop.getProperty(key);

        if (requiredProperty && val == null)
            throw new NullPointerException("Value for " + key + " should NOT be null! Fix config file: " + file);

        return val;
    }

    protected void set(String key, String val) {
        prop.setProperty(key, val);
    }

    @Override
    public String toString() {
        String str = "";
        for (Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            String val = entry.getValue().toString();
            if (key.toLowerCase().contains("secure") || key.toLowerCase().contains("password")
                    || key.toLowerCase().contains("secret"))
                continue;

            str += key + "=" + val + ";  ";
        }
        return str;
    }
}
