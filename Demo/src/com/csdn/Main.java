package com.csdn;

import com.vmware.common.samples.SystemParameters;
import com.vmware.common.ssl.TrustAll;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Main {

    public static void trustAll() throws NoSuchAlgorithmException, KeyManagementException {
        TrustAll.trust();
    }

    public static void main(String[] args) throws Throwable {
        SystemParameters systemParameters;
        systemParameters = SystemParameters.parse(findConfigurationFiles());
        Iterator i$ = systemParameters.parameterNames().iterator();
        while (i$.hasNext()) {
            String propName = (String) i$.next();
            String value = systemParameters.get(propName);
            System.setProperty(propName, value);
        }
        trustAll();
        Connect connect = new Connect("https://172.17.7.249/sdk", "administrator@vsphere.local", "!!!Jcb410");
//        connect.getAllVirtualMachine();
        connect.getVirtualMachineConfig();
    }


    private static File[] findConfigurationFiles() {
        LinkedList<File> files = new LinkedList<>();
        String[] arr = propertyFileNames();

        for (String fileName : arr) {
            try {
                List<File> e = findPropertiesFile(fileName.replaceAll("(^\\s+|\\s+$)", ""));
                files.addAll(e);
            } catch (URISyntaxException var6) {
                var6.printStackTrace();
            }
        }

        return files.toArray(new File[files.size()]);
    }


    public static String[] propertyFileNames() {
        String specified = System.getProperty("sample.properties.files");
        return specified != null && !"".equals(specified) ? specified.split("\\s*,\\s*") : scanForPropertyFiles();
    }


    public static String[] scanForPropertyFiles() {
        File[] files = (new File(".")).listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.endsWith(".properties");
            }
        });
        String[] names = new String[files.length];

        for (int i = 0; i < names.length; ++i) {
            names[i] = files[i].getName();
        }

        return names;
    }


    public static List<File> findPropertiesFile(String fileName) throws URISyntaxException {
        if (null != fileName && fileName.length() != 0) {
            LinkedList<File> files = new LinkedList<>();
            URL resourceUrl = Main.class.getResource(fileName);
            if (resourceUrl != null) {
                URI workingDir = resourceUrl.toURI();
                files.add(new File(workingDir));
            }

            File workingDir1 = new File(".");
            checkForProperties(workingDir1, fileName, files);
            File homeDir = new File(System.getProperty("user.home"));
            checkForProperties(homeDir, fileName, files);
            return files;
        } else {
            return new LinkedList<>();
        }
    }


    private static void checkForProperties(File dir, String fileName, List<File> files) {
        File file = new File(dir, fileName);
        if (file.exists() && file.canRead()) {
            files.add(file);
        }
    }
}
