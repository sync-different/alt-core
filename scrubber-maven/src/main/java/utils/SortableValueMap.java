/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package utils;

/**
 * Sortable value map
 */
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sortable HashMap
 * @param <K>
 * @param <V>
 */
public class SortableValueMap<K, V extends Comparable<V>> extends LinkedHashMap<K, V> {

    /**
     * Constructor
     */
    public SortableValueMap() {
    }

    /**
     * Constructor
     * @param map
     */
    public SortableValueMap(Map<K, V> map) {
        super(map);
    }

    /**
     * Hash sorted by value sort by values
     */
    public void sortByValue() {
        List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> entry1, Map.Entry<K, V> entry2) {
                return entry2.getValue().compareTo(entry1.getValue());
            }
        });
        clear();
        for (Map.Entry<K, V> entry : list) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Print
     * @param text
     * @param map
     */
    private static void print(String text, Map<String, Double> map) {
        System.out.println(text);
        for (String key : map.keySet()) {
            System.out.println("key/value: " + key + "/" + map.get(key));
        }
    }

    /**
     * test
     */
    public static void main(String[] args) {
        SortableValueMap<String, Double> map = new SortableValueMap<String, Double>();

        map.put("A", 67.5);
        map.put("B", 99.5);
        map.put("C", 82.4);
        map.put("D", 42.0);

        print("Unsorted map", map);
        map.sortByValue();
        print("Sorted map", map);
    }
}
