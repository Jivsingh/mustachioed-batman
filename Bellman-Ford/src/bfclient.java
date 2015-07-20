import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;


public class bfclient {
	private boolean client_running = false, logging = false;
	private int localport;
	private int timeout;
	private String my_name = null;
	private String IP = null;
	private String valid_commands = "1. LINKDOWN {ip_address port} \n2. LINKUP {ip_address port} \n"
			+ "3. SHOWRT \n4. CLOSE \n5. CHANGECOST {ip_address port cost} \n6. TRANSFER {filename destination_ip port}";
	private DatagramSocket Socket = null;
	
	/**
	 * Record of ALL neighbors
	 */
	private Hashtable<String, Neighbor_Record> neighbors = new Hashtable<String, Neighbor_Record>();
	
	/**
	 * Destination, Cost tuple for all nodes
	 */
	private Hashtable<String, Double> distance_vector = new Hashtable<String, Double>();   //distance to all nodes
	
	/**
	 * Destination, Next Hop tuple for all nodes
	 */
	private Hashtable<String, String> route_table = new Hashtable<String, String>();    //destination, next hop tuple
	
	static final double INFINITY = 99999999;
	
	private static Object Lock =  new Object();
	
	public bfclient(String config_filename) throws UnknownHostException {
		loadConfig(config_filename);
		IP = InetAddress.getLocalHost().getHostAddress();
		IP = "127.0.0.1";
		my_name = IP + ":" + localport;
		distance_vector.put(my_name, (double) 0);
		route_table.put(my_name, my_name);
		try {
			Socket = new DatagramSocket(localport);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		startRouter();
	}
	
	/**
	 * Initialize nodes by reading config from file
	 */
	private void loadConfig(String filename) {
		double cost;
		String sdir = System.getProperty("user.dir");
		String sep = System.getProperty("file.separator");
		File config_file = new File(sdir + sep + filename);
		String[] word;
//		String [] name;
		try {
			BufferedReader input = new BufferedReader(new FileReader(config_file));
			String node_info = input.readLine();
			while(node_info == "\n") {
				node_info = input.readLine();
			}
			word = node_info.split(" ");
			localport = Integer.parseInt(word[0]);
			timeout = Integer.parseInt(word[1]);
			
			String neighbor_info = input.readLine();
			
			while (neighbor_info != null) {
				if (neighbor_info.isEmpty() || neighbor_info.trim().equals("") || neighbor_info.trim().equals("\n")) {
					neighbor_info = input.readLine();
					continue;
				}
				//Add record for the neighbor
				word = neighbor_info.split(" ");
				Neighbor_Record neighbor = new Neighbor_Record(word[0]);
				cost = Double.parseDouble(word[1]);
				if (cost < INFINITY) {
					neighbor.initial_distance = cost;
					neighbor.setLinkCost(neighbor.initial_distance);
				} else {
					System.out.println("Neighbor link cost cannot be >= "+INFINITY);
					throw new NumberFormatException();
				}
				neighbor.isOnline = true;
				neighbor.last_LIVEtime = System.currentTimeMillis();
				neighbors.put(word[0], neighbor);
				
				//Initialize other tables too
				distance_vector.put(word[0], Double.parseDouble(word[1]));
				route_table.put(word[0], word[0]);
				
				neighbor_info = input.readLine();
			}
			input.close();
		} catch (FileNotFoundException e) {
			System.out
					.println("File not found! Ensure client config file is in the base project folder.");
			e.printStackTrace();
		} catch (UnknownHostException e) {
			System.out.println("Enter valid IP Addresses or hostnames for neighbors!");
			System.out.println("Neighbor should be specified as 'IPAddress:port_number distance'");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		printNeighbors();
	}
	
	
	
	
	/**
	 * Thread which listens for incoming messages
	 * @author Jivtesh
	 */
	private class Listener extends Thread {
		byte[] receiveData = new byte[1400];
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		Message message = null;

		public void run() {
			while (client_running) {
				try {
					Socket.receive(receivePacket);
					ByteArrayInputStream bis = new ByteArrayInputStream(
							receivePacket.getData());
					ObjectInputStream in = new ObjectInputStream(bis);
					message = (Message) in.readObject();
					processMessage(message);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Processes receives messages
	 * @param message
	 * @throws UnknownHostException 
	 */
	private void processMessage (Message message) throws UnknownHostException {
		
		switch (message.getType()) {
		case ROUTE_UPDATE:
			boolean change = false;
			if (logging) {
			System.out.println("Received a route update from "+message.sender);			
			}
			//Get the distance vector from the message
			Neighbor_Record neighbor;
			Hashtable<String, Double> neighbor_vector = (Hashtable<String, Double>) message.getValue();
			
			//A new neighbor is discovered
			if (!neighbors.containsKey(message.sender)) {
				neighbor = new Neighbor_Record(message.sender);
				neighbor.initial_distance = neighbor_vector.get(my_name);
				neighbor.setLinkCost(neighbor.initial_distance);
				neighbor.last_LIVEtime = System.currentTimeMillis();
				neighbor.isOnline = true;
				neighbors.put(message.sender, neighbor);
				
				synchronized (Lock) {
					distance_vector.put(message.sender, neighbor_vector.get(my_name));
					route_table.put(message.sender, message.sender);
					System.out.println("Discovered neighbor");
				}
				change = true;
			} else if (false) {
				
			} else {
				neighbor = neighbors.get(message.sender);  //Known neighbor
				neighbor.last_LIVEtime = System.currentTimeMillis();
			}
			
			Double neighbor_cost, cost, total_cost;			
			
			
			if (neighbor_vector != null) {
				neighbor.setDistance_vector(neighbor_vector);
				if (logging) {
					System.out
							.println(message.sender + " has distance vector:");
					System.out.println(neighbor_vector.toString());
				}
			}
			
			//For every destination in the received DV
			for (String destination_name : neighbor_vector.keySet()) {
				synchronized (Lock) {
					cost = neighbor_vector.get(destination_name);
					neighbor_cost = distance_vector.get(message.sender);
					total_cost = neighbor_cost + cost;
					
					//New destination discovered
					if (!distance_vector.containsKey(destination_name)) {
						distance_vector.put(destination_name, total_cost);
						System.out.println("New destination "+destination_name+" discovered!");
						route_table.put(destination_name, message.sender);
						change = true;
					}
					
					//Shorter path discovered
					if (distance_vector.get(destination_name) > total_cost) {
						System.out.println("Shorter path discovered to "+destination_name+" discovered!");
						distance_vector.put(destination_name, total_cost);
						System.out.println(distance_vector.toString());
						route_table.put(destination_name, message.sender);
						change = true;
					}
				}			
			}
			
			// If the sender is a next hop to somewhere
			synchronized (Lock) {
				for (String destination_name : route_table.keySet()) {
					// If the sender is a next hop to somewhere
					if (route_table.get(destination_name)
							.equals(message.sender)) {
						cost = neighbor_vector.get(destination_name);
						neighbor_cost = distance_vector.get(message.sender);
						total_cost = neighbor_cost + cost;
						if (cost < INFINITY) {
							//TODO: Back-Forth here
							if (distance_vector.get(destination_name) < cost) {
								distance_vector.put(destination_name,
										total_cost);
								change = true;
							}
						} else {
							distance_vector.put(destination_name, INFINITY);
							route_table.remove(destination_name);
							change = true;
						}
					}
				}
			}
			
			//send updates in the background right away
			if (change) {
				change = false;
				new Thread (new Runnable() {					
					@Override
					public void run() {
						sendUpdates();
						System.out.println("Runnable was spawned!");
					}
				}).start();
			}
			break;

		default:
			break;
		}
	}
	
	/**
	 * Checks whether neighbors are online every timeout/10 seconds
	 * @author Jivtesh
	 *
	 */
	private class checkLIVE extends Thread {		
		public void run() {
			Neighbor_Record neighbor;
			long time_sinceUpdate;
			boolean change = false;
			while (client_running) {
				//TODO: Dedicated locking
				synchronized (Lock) {
					for (String name : neighbors.keySet()) {
						neighbor = neighbors.get(name);
						time_sinceUpdate = System.currentTimeMillis() - neighbor.last_LIVEtime;
//						System.out.println("Time since update = "+time_sinceUpdate);
						if ((time_sinceUpdate > 3*timeout*1000) && (neighbor.isOnline)) {
							neighbor.isOnline = false;
							distance_vector.put(name, INFINITY);
							route_table.remove(name);
							System.out.println("Neighbor "+neighbor.name+" was removed!");
							change = true;
						}
					}
				}
				if (change) {
					sendUpdates();
					change = false;
				}
				try {
					Thread.sleep(timeout/10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Sends updates at every 'timeout' interval
	 * @author Jivtesh
	 *
	 */
	private class Heartbeat extends TimerTask {		
		public void run() {
			sendUpdates();
		}
	}
	
	
	/**
	 * Send route updates to all neighbors
	 */
	private void sendUpdates() {
		Neighbor_Record neighbor;
		synchronized (Lock) {
			for (String name : neighbors.keySet()) {
				neighbor = neighbors.get(name);
				if (neighbor.isOnline) {
					sendUpdate(neighbor);
				}
			}
		}
	}
	
	/**
	 * Send update to a particular neighbor
	 * @param neighbor
	 */
	private void sendUpdate(Neighbor_Record neighbor) {
		ByteArrayOutputStream bos;
		ObjectOutputStream o_out = null;
		byte[] data = new byte[1400];
		Message updateMessage;

		
		// Pointer like!!!!!!!!!
		// Poison!
		neighbor.distance_vector_toSend = (Hashtable<String, Double>) distance_vector.clone();
		synchronized (Lock) {
			for (String destination : route_table.keySet()) {
				if ((route_table.get(destination).equals(neighbor.name)) && !(destination.equals(neighbor.name))) {
					neighbor.distance_vector_toSend.put(destination, INFINITY);
				}
			}
		}
		updateMessage = new Message(Message.Type.ROUTE_UPDATE,
				neighbor.distance_vector_toSend);
		updateMessage.sender = my_name;
		try {
			bos = new ByteArrayOutputStream();
			o_out = new ObjectOutputStream(bos);
			o_out.writeObject(updateMessage);
			o_out.flush();
			data = bos.toByteArray();
			DatagramPacket updatePacket = new DatagramPacket(data, data.length,
					neighbor.getIP(), neighbor.getPort_number());
			Socket.send(updatePacket);
			if (logging) {
				System.out.println("Update sent to neighbor " + neighbor.name);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private void startRouter() {
		client_running = true;

		new Listener().start();
		new checkLIVE().start();

		Timer timer = new Timer();
		timer.schedule(new Heartbeat(), 0, timeout * 1000);

		String command, input, param;
		String[] word;
		int index;

		@SuppressWarnings("resource")
		Scanner command_scanner = new Scanner(System.in);

		while (client_running) {
			System.out.print("%>");
			if (command_scanner.hasNextLine()) {
				input = command_scanner.nextLine().trim();
				word = input.split(" ");

				// Command handling decision tree
				if (word[0].equalsIgnoreCase("showrt")) {
					printTables();
				} else if (word[0].equalsIgnoreCase("linkup")) {

				} else if (word[0].equalsIgnoreCase("linkdown")) {

				} else if (word[0].equalsIgnoreCase("close")) {

				} else if (word[0].equalsIgnoreCase("changecost")) {

				} else if (word[0].equalsIgnoreCase("transfer")) {

				} else {
					System.out.println("ERROR!! Invalid Command!");
					System.out.println("Valid commands are of the form:- ");
					System.out.println(valid_commands);
				} // end of decision tree

			} // nextLine read

		} // while

	} // startRouter
	
	
	/**
	 * Print DV and Routing tables
	 */
	private void printTables() {
		// Neighbor_Record neighbor;
		Date date = new Date();
		SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
		System.out.println("Routes known at time " + fmt.format(date) + "(current time) are:- ");
		synchronized (Lock) {
			for (String name : distance_vector.keySet()) {
				if (true) {
					System.out.println("Destination = " + name + ", Cost = "
							+ distance_vector.get(name) + ", Link = ("
							+ route_table.get(name) + ")");
				}
			}
		}
	}
	
	
	public static void main(String argv[]) throws UnknownHostException {
		new bfclient(argv[0]);
	}
	
}