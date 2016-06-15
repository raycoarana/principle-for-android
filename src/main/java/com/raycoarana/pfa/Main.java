package com.raycoarana.pfa;

import com.dd.plist.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        System.out.println("Principle for android v0.1");

        String fileToConvert = args[0];
        try {
            NSObject propertyList = BinaryPropertyListParser.parse(new File(fileToConvert));

            NSDictionary rootDictionary = (NSDictionary) propertyList;
            NSDictionary top = (NSDictionary) rootDictionary.get("$top");
            UID rootId = (UID) top.get("root");
            NSObject[] objects = ((NSArray) rootDictionary.get("$objects")).getArray();
            Map<Integer, Object> cache = new HashMap<>();
            Object rootObject = getValue(objects, cache, rootId);

            System.out.println("File parsed");
            //PropertyListParser.saveAsXML(propertyList, new File(fileToConvert + ".xml"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PropertyListFormatException e) {
            e.printStackTrace();
        }
    }

    private static Object instanceObject(NSObject[] objects, Map<Integer, Object> cache, NSDictionary rawObject) {
        String className = getClassName(objects, rawObject);
        switch (className) {
            case "NSMutableArray":
                return mapToList(objects, cache, rawObject);
            case "NSMutableSet":
                return mapToSet(objects, cache, rawObject);
            case "NSMutableData":
                return mapToData(rawObject);
            case "NSMutableDictionary":
                return mapToMap(objects, cache, rawObject);
            default:
                return mapToClass(objects, cache, className, rawObject);
        }
    }

    private static Map mapToMap(NSObject[] objects, Map<Integer, Object> cache, NSDictionary rawObject) {
        HashMap hashMap = new HashMap(rawObject.count());
        for (Map.Entry<String, NSObject> entry : rawObject.entrySet()) {
            hashMap.put(entry.getKey(), getValue(objects, cache, entry.getValue()));
        }

        return hashMap;
    }

    private static byte[] mapToData(NSDictionary rawObject) {
        NSData data = ((NSData)rawObject.get("NS.data"));
        return data.bytes();
    }

    private static Set mapToSet(NSObject[] objects, Map<Integer, Object> cache, NSDictionary rawObject) {
        NSObject[] items = ((NSArray)rawObject.get("NS.objects")).getArray();
        HashSet set = new HashSet(items.length);
        for (NSObject item : items) {
            set.add(getValue(objects, cache, item));
        }
        return set;
    }

    private static List mapToList(NSObject[] objects, Map<Integer, Object> cache, NSDictionary rawObject) {
        NSArray nsArray = (NSArray)rawObject.get("NS.objects");
        return mapNSArrayToList(objects, cache, nsArray);
    }

    private static List mapNSArrayToList(NSObject[] objects, Map<Integer, Object> cache, NSArray nsArray) {
        NSObject[] items = nsArray.getArray();
        ArrayList arrayList = new ArrayList(items.length);
        for (NSObject item : items) {
            arrayList.add(getValue(objects, cache, item));
        }
        return arrayList;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapToClass(NSObject[] objects, Map<Integer, Object> cache, String className, NSDictionary rawObject) {
        try {
            Class<?> clazz = Class.forName(className);
            T instance = (T) clazz.newInstance();
            for (String key : rawObject.allKeys()) {
                if ("$class".equals(key)) {
                    continue;
                }

                Field field = clazz.getField(javalize(key));
                field.set(instance, getValue(objects, cache, rawObject.get(key)));
            }
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to map class " + className + ": " + ex.getMessage(), ex);
        }
    }

    private static String javalize(String key) {
        return key.replace('.', '_');
    }

    private static Object getValue(NSObject[] objects, Map<Integer, Object> cache, NSObject nsObject) {
        if (nsObject instanceof NSString) {
            String value = ((NSString) nsObject).getContent();
            return value.equals("$null") ? null : value;
        } else if (nsObject instanceof UID) {
            long index = getIndex((UID) nsObject);
            if (index >= objects.length) {
                System.out.println("Warning: UID out of bounds:" + Long.toHexString(index));
                return null;
            }
            Object value = cache.get((int)index);
            if (value != null) {
                return value;
            } else {
                NSObject object = objects[(int) index];
                if (object instanceof NSDictionary) {
                    NSDictionary rawObject = (NSDictionary) object;
                    value = instanceObject(objects, cache, rawObject);
                } else {
                    value = getValue(objects, cache, object);
                }
                cache.put((int)index, value);
                return value;
            }
        } else if (nsObject instanceof NSNumber) {
            NSNumber nsNumber = ((NSNumber) nsObject);
            if (nsNumber.isBoolean()) {
                return nsNumber.boolValue();
            } else if (nsNumber.isInteger()) {
                return nsNumber.intValue();
            } else {
                return nsNumber.floatValue();
            }
        } else if (nsObject instanceof NSData) {
            return ((NSData) nsObject).bytes();
        } else if (nsObject instanceof NSArray) {
            return mapNSArrayToList(objects, cache, (NSArray) nsObject);
        }
        throw new RuntimeException("Can't get value for type: " + nsObject.getClass().getName());
    }

    private static String getClassName(NSObject[] objects, NSDictionary dictionary) {
        NSDictionary classInfo;
        if (dictionary.containsKey("$class")) {
            UID classUID = (UID) dictionary.get("$class");
            int index = (int) getIndex(classUID);
            classInfo = (NSDictionary) objects[index];
        } else {
            classInfo = dictionary;
        }
        return ((NSString)classInfo.get("$classname")).getContent();
    }

    public static long getIndex(UID uid) {
        StringBuilder ascii = new StringBuilder();
        for(int i = 0; i < uid.getBytes().length; ++i) {
            byte b = uid.getBytes()[i];
            if(b < 16) {
                ascii.append("0");
            }

            ascii.append(Integer.toHexString(b));
        }
        return Long.valueOf(ascii.toString(), 16);
    }

}
