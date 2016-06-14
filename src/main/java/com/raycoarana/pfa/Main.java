package com.raycoarana.pfa;

import com.dd.plist.BinaryPropertyListParser;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        System.out.println("Principle for android v0.1");

        String fileToConvert = args[0];
        try {
            NSObject propertyList = BinaryPropertyListParser.parse(new File(fileToConvert));
            PropertyListParser.saveAsXML(propertyList, new File(fileToConvert + ".xml"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PropertyListFormatException e) {
            e.printStackTrace();
        }
    }

}
