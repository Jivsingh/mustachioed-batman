import java.net.InetAddress;
import java.util.HashSet;

public class User_Record {

	public boolean isOnline = false, isAuthenticated = false,
			isBlocked = false;
	public int tries_left = 3;
	public long block_Starttime;
	public long last_LIVEtime = 0;

	private String name;
	private String Offline_Messages;
	private HashSet<String> blacklist;
	private int port_number = 0;
	private String IP = null;

	public User_Record(String name) {
		this.name = name;
		blacklist = new HashSet<String>();
		Offline_Messages = new String();
	}

	public synchronized HashSet<String> getBlacklist() {
		return blacklist;
	}

	public synchronized int getPort_number() {
		return port_number;
	}

	public synchronized void setPort_number(int port_number) {
		this.port_number = port_number;
	}

	public synchronized String getIP() {
		return IP;
	}

	public synchronized void setIP(String iP) {
		IP = iP;
	}

}
