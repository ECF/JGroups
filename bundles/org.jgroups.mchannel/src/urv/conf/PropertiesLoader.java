package urv.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * This class provide an easy access to the system properties
 * defined in the configuration properties file
 * 
 * @author Marcel Arrufat Arias
 */
public class PropertiesLoader {
	
	//	CONSTANTS --
	
    private static PropertiesLoader instance;
    private static final String EMULATED = "EMULATED";
    private static final String COMM_LOG = "COMM_LOG";
    private static final String UNICAST_PORT = "UNICAST_PORT";
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String RELIABILITY = "RELIABILITY";
    private static final String DYNAMIC_CREDIT = "DYNAMIC_CREDIT";
    private static final String THROUGHPUT_OPTIMIZATION_HOP_COUNT = "THROUGHPUT_OPTIMIZATION_HOP_COUNT";
    private static final String THROUGHPUT_OPTIMIZATION_NETWORK_SELF_KNOWLEGEMENT = "THROUGHPUT_OPTIMIZATION_NETWORK_SELF_KNOWLEGEMENT";
    private static final String MULTICAST_PROTOCOL = "MULTICAST_PROTOCOL";
	private static final String GRAPH_FILE = "GRAPH_FILE";
    private static final String SENDING_PROB = "SENDING_PROB";
    private static final String APPLICATION = "APPLICATION";
    private static final String EMULATION_TASKS = "EMULATION_TASKS";
    
    //	CLASS FIELDS --
    
    private Properties props;
    private String fileName;
    private Properties defaults = null;

    //	CONSTRUCTORS --
    
    private PropertiesLoader() {
        fileName=System.getProperty("user.dir")+"/conf/omolsr.properties";
        defaults=new Properties();
        defaults.setProperty(EMULATED, "false");
        defaults.setProperty(COMM_LOG, "true");
        defaults.setProperty(UNICAST_PORT, "5034");
        defaults.setProperty(CHANNEL_ID, "CHANNEL_URV");
        defaults.setProperty(RELIABILITY, "true");
        defaults.setProperty(THROUGHPUT_OPTIMIZATION_HOP_COUNT, "false");  
        defaults.setProperty(DYNAMIC_CREDIT, "true");  
        defaults.setProperty(MULTICAST_PROTOCOL, "omolsr");
        defaults.setProperty(GRAPH_FILE, "resources/graphs/graph4nodesLine.net");
        defaults.setProperty(APPLICATION, "urv.app.samples.SimpleSenderApplication");
        defaults.setProperty(EMULATION_TASKS, "");
        defaults.setProperty(SENDING_PROB, "1.0");

        props=new Properties(defaults);
        try {
            load();
            dump();
        } catch (FileNotFoundException ex) {
            System.err.println("Properties file not found. I will create one for you with default values");
            try {
                this.saveProperties();
            } catch (IOException e) {
                System.err.println("Warning: Cannot save properties file - Will use defaults anyway");
            }
        } catch (IOException ex) {
            System.err.println("Warning: Cannot read Init file - Turning to default values");
        }
    }

    //	STATIC METHODS --
    
    public static String getApplication() {
        return getInstance().props.getProperty(APPLICATION);
    }
    public static String getChannelId() {
        return getInstance().props.getProperty(CHANNEL_ID);
    }
    public static String[] getEmulationTasks() {
    	String tasks = getInstance().props.getProperty(EMULATION_TASKS);
    	if (tasks.equals("")){
    		return new String[]{};
    	} else {
    		return tasks.split(",");
    	}
    }
    public static String getGraphFile() {
    	String graphFile = getInstance().props.getProperty(GRAPH_FILE);
    	if (graphFile.contains("\\"))
    		graphFile = graphFile.replace("\\", ""+File.separatorChar);
        return graphFile;
    }
    public static PropertiesLoader getInstance(){
        if(instance==null) {
            instance=new PropertiesLoader();
        }
        return instance;
    }
    public static String getMulticastProtocol() {
        return getInstance().props.getProperty(MULTICAST_PROTOCOL);
    }
    public static float getSendingProb() {
        return Float.valueOf(getInstance().props.
                getProperty(SENDING_PROB)).floatValue();
    }
    public static int getUnicastPort() {
        return Integer.valueOf(getInstance().props.
                getProperty(UNICAST_PORT)).intValue();
    }
    public static boolean isCommunicationLog() {
        return Boolean.valueOf(getInstance().props.getProperty(COMM_LOG)).
                booleanValue();
    }
    public static boolean isDynamicCredit() {
		return Boolean.valueOf(getInstance().props.getProperty(DYNAMIC_CREDIT))
			.booleanValue();
	}
    public static boolean isEmulated() {
        return Boolean.valueOf(getInstance().props.getProperty(EMULATED)).
                booleanValue();
    }
    public static boolean isReliabilityEnabled() {
        return Boolean.valueOf(getInstance().props.getProperty(RELIABILITY)).
                booleanValue();
    }
    public static boolean isThroughputOptimizationHopCountEnabled() {
		return Boolean.valueOf(getInstance().props.getProperty(THROUGHPUT_OPTIMIZATION_HOP_COUNT))
			.booleanValue();
	}
    public static boolean isThroughputOptimizationNetworkSelfKnowledgementEnabled() {
		return Boolean.valueOf(getInstance().props.getProperty(THROUGHPUT_OPTIMIZATION_NETWORK_SELF_KNOWLEGEMENT))
			.booleanValue();
	}    
    
    //	PUBLIC METHODS --
    
    public void changeFile(String filename) throws FileNotFoundException, IOException{
        this.fileName=filename;
        this.load();
    }    
    public void dump(){
        props.list(System.out);
        System.out.println();
    }
	public void saveProperties() throws  IOException{
        this.saveProperties(this.fileName);
    }	
	public void saveProperties(String filename) throws IOException {
        this.fileName=filename;
        props.store(new FileOutputStream(this.fileName), "Popeye properties file");
    }	
	private void load() throws FileNotFoundException, IOException {
        props.load(new FileInputStream(fileName));
    }
}