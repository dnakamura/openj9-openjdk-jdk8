/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import  java.io.BufferedWriter;
import  java.io.File;
import  java.io.FileWriter;
import  java.io.IOException;
import  java.util.HashMap;
import  java.util.List;
import  java.util.Map;
import  java.util.Set;
import  java.util.SortedMap;
import  java.util.TreeMap;
import  java.util.TreeSet;

/**
 * <code>Simple</code> generates TimeZoneData, which had been used as internal
 * data of TimeZone before J2SDK1.3.
 * Since J2SDK1.4 doesn't need TimeZoneData, this class is for maintenance
 * of old JDK release.
 */
class Simple extends BackEnd {

    /**
     * Zone records which are applied for given year.
     */
    private static Map<String,ZoneRec> lastZoneRecs = new HashMap<>();

    /**
     * Rule records which are applied for given year.
     */
    private static Map<String,List<RuleRec>> lastRules = new TreeMap<>();

    /**
     * zone IDs sorted by their GMT offsets. If zone's GMT
     * offset will change in the future, its last known offset is
     * used.
     */
    private SortedMap<Integer, Set<String>> zonesByOffset = new TreeMap<>();

    /**
     * Sets last Rule records and Zone records for given timezone to
     * each Map.
     *
     * @param tz Timezone object for each zone
     * @return always 0
     */
    int processZoneinfo(Timezone tz) {
        String zonename = tz.getName();

        lastRules.put(zonename, tz.getLastRules());
        lastZoneRecs.put(zonename, tz.getLastZoneRec());

        // Populate zonesByOffset. (Zones that will change their
        // GMT offsets are also added to zonesByOffset here.)
        int lastKnownOffset = tz.getRawOffset();
        Set<String> set = zonesByOffset.get(lastKnownOffset);
        if (set == null) {
            set = new TreeSet<>();
            zonesByOffset.put(lastKnownOffset, set);
        }
        set.add(zonename);

        return 0;
    }

    /**
     * Generates TimeZoneData to output SimpleTimeZone data.
     * @param map Mappings object which is generated by {@link Main#compile}.
     * @return 0 if no error occurred, otherwise 1.
     */
    int generateSrc(Mappings map) {
        try {
            File outD = new File(Main.getOutputDir());
            outD.mkdirs();

            FileWriter fw =
                new FileWriter(new File(outD, "TimeZoneData.java"), false);
            BufferedWriter out = new BufferedWriter(fw);

            out.write("import java.util.SimpleTimeZone;\n\n");
            out.write("    static SimpleTimeZone zones[] = {\n");

            Map<String,String> a = map.getAliases();
            List<Integer> roi = map.getRawOffsetsIndex();
            List<Set<String>> roit = map.getRawOffsetsIndexTable();

            int index = 0;
            for (int offset : zonesByOffset.keySet()) {
                int o = roi.get(index);
                Set<String> set = zonesByOffset.get(offset);
                if (offset == o) {
                    // Merge aliases into zonesByOffset
                    set.addAll(roit.get(index));
                }
                index++;

                for (String key : set) {
                    ZoneRec zrec;
                    String realname;
                    List<RuleRec> stz;
                    if ((realname = a.get(key)) != null) {
                        // if this alias is not targeted, ignore it.
                        if (!Zone.isTargetZone(key)) {
                            continue;
                        }
                        stz = lastRules.get(realname);
                        zrec = lastZoneRecs.get(realname);
                    } else {
                        stz = lastRules.get(key);
                        zrec = lastZoneRecs.get(key);
                    }

                    out.write("\t//--------------------------------------------------------------------\n");
                    String s = Time.toFormedString(offset);
                    out.write("\tnew SimpleTimeZone(" +
                        Time.toFormedString(offset) + ", \"" + key + "\"");
                    if (realname != null) {
                        out.write(" /* " + realname + " */");
                    }

                    if (stz == null) {
                        out.write("),\n");
                    } else {
                        RuleRec rr0 = stz.get(0);
                        RuleRec rr1 = stz.get(1);

                        out.write(",\n\t  " + Month.toString(rr0.getMonthNum()) +
                                  ", " + rr0.getDay().getDayForSimpleTimeZone() + ", " +
                                  rr0.getDay().getDayOfWeekForSimpleTimeZone() + ", " +
                                  Time.toFormedString((int)rr0.getTime().getTime()) + ", " +
                                  rr0.getTime().getTypeForSimpleTimeZone() + ",\n" +

                                  "\t  " + Month.toString(rr1.getMonthNum()) + ", " +
                                  rr1.getDay().getDayForSimpleTimeZone() + ", " +
                                  rr1.getDay().getDayOfWeekForSimpleTimeZone() + ", " +
                                  Time.toFormedString((int)rr1.getTime().getTime())+ ", " +
                                  rr1.getTime().getTypeForSimpleTimeZone() + ",\n" +

                                  "\t  " + Time.toFormedString(rr0.getSave()) + "),\n");

                        out.write("\t// " + rr0.getLine() + "\n");
                        out.write("\t// " + rr1.getLine() + "\n");
                    }

                    String zline = zrec.getLine();
                    if (zline.indexOf("Zone") == -1) {
                        zline = "Zone " + key + "\t" + zline.trim();
                    }
                    out.write("\t// " + zline + "\n");
                }
            }
            out.write("    };\n");

            out.close();
            fw.close();
        } catch(IOException e) {
            Main.panic("IO error: "+e.getMessage());
            return 1;
        }

        return 0;
    }
}
