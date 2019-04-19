package org.joget.commons.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import org.apache.commons.io.FileUtils;

public class ServerUtil {
    public static final String SYSTEM_PROPERTY_NODE_NAME = "wflow.name";
    
    protected static String serverName = null;
    protected static Map<String, Runnable> cleaningTasks = new HashMap<String, Runnable>();
    protected static Map<String, Runnable> allServersCleaningTasks = new HashMap<String, Runnable>();
    
    public static void addServerShutdownCleaningTask(String name, Runnable runnable) {
        if (!cleaningTasks.containsKey(name)) {
            cleaningTasks.put(name, runnable);
        }
    }
    
    public static void addAllServersShutdownCleaningTask(String name, Runnable runnable) {
        if (!allServersCleaningTasks.containsKey(name)) {
            allServersCleaningTasks.put(name, runnable);
        }
    }
    
    public static void registerServer() {
        writeServer();
    }
    
    public static void unregisterServer() {
        writeServer();
    }
    
    public static String getServerName() {
        if (serverName == null) {
            serverName = System.getProperty(SYSTEM_PROPERTY_NODE_NAME, getEndPoint()); //To support override node name
        }
        return serverName;
    }
    
    protected static void writeServer() {
        Set<String> servers = new HashSet<String>();
        Gson gson = new Gson();
                
        String serverFilePath = SetupManager.getBaseSharedDirectory() + "/servers.json";
        
        try {
            String serverJson = FileUtils.readFileToString(new File(serverFilePath));
            servers = gson.fromJson(serverJson, new TypeToken<Set<String>>(){}.getType());
        } catch (Exception e) {
            LogUtil.debug(ServerUtil.class.getName(), "Error read servers file: " + e.getMessage());
        }
        
        String lServerName = getServerName();
        
        if (!servers.contains(lServerName)) {
            servers.add(lServerName);
        } else {
            servers.remove(lServerName);

            if (!cleaningTasks.isEmpty()) {
                for (Runnable runnable : cleaningTasks.values()) {
                    runnable.run();
                }
            }

            if (servers.isEmpty()) {
                if (!allServersCleaningTasks.isEmpty()) {
                    for (Runnable runnable : allServersCleaningTasks.values()) {
                        runnable.run();
                    }
                }
            }

            serverName = null;
        }
        
        try {
            String serverJson = gson.toJson(servers);
            FileUtils.writeStringToFile(new File(serverFilePath), serverJson, "UTF-8");
        } catch (Exception e) {
            LogUtil.debug(ServerUtil.class.getName(), "Error write servers file: " + e.getMessage());
        }
    }
    
    protected static String getEndPoint() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> objs = mbs.queryNames( new ObjectName( "*:type=Connector,*" ),
                    Query.match( Query.attr( "protocol" ), Query.value( "HTTP/1.1" ) ) );
            String hostname = InetAddress.getLocalHost().getHostName();
            for ( ObjectName obj : objs ) {
                String port = obj.getKeyProperty( "port" );
                return (hostname + "_" + port).replaceAll("[^a-zA-Z0-9_-]", "");
            }
        } catch (Exception ex ) {
            LogUtil.error(ServerUtil.class.getName(), ex, "");
        }
        return "";
    }
}
