import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;



/**
 * Contains ALL the information a node has about a particular neighbor including:-
 * IP address and port number on which it listens,
 * Cost/Distance to neighbor and
 * Neighbor's distance vector
 * 
 */
public class Neighbor_Record {
	public boolean isOnline = false;
	public int tries_left = 3;
	public long last_LIVEtime;

	public String name;
	private int port_number = 0;
	private InetAddress IP = null;
	
	/**
	 * sent on updates
	 */
	private double link_cost;
	
	/**
	 * Set on the config file
	 */
	double initial_distance;
	
	/**
	 * From this neighbor to all nodes
	 * 
	 */
	Hashtable<String, Double> distance_vector = new Hashtable<String, Double>();
	
	/**
	 * To be sent to this neighbor
	 * 
	 */
	Hashtable<String, Double> distance_vector_toSend = new Hashtable<String, Double>();
	
	public Hashtable<String, Double> getDistance_vector() {
		return distance_vector;
	}

	public void setDistance_vector(Hashtable<String, Double> distance_vector) {
		this.distance_vector = distance_vector;
	}

	public Neighbor_Record(String name) throws UnknownHostException {
		this.name = name;
		String word[] = name.split(":");
		IP = InetAddress.getByName(word[0]);
		port_number = Integer.parseInt(word[1]);
	}

//	public synchronized HashSet<String> getBlacklist() {
//		return blacklist;
//	}

	public void printRecord() {
		System.out.println("IP Address = "+IP.getHostAddress());
		System.out.println("Port number = "+port_number);
		System.out.println("Distance/Cost = "+link_cost+"\n");
	}
	
	public synchronized int getPort_number() {
		return port_number;
	}

	public double getLinkCost() {
		return link_cost;
	}

	public void setLinkCost(double distance) {
		this.link_cost = distance;
	}

	public synchronized void setPort_number(int port_number) {
		this.port_number = port_number;
	}

	public synchronized InetAddress getIP() {
		return IP;
	}

	public synchronized void setIP(InetAddress iP) {
		IP = iP;
	}

}