import java.io.*;
import java.net.*;
import java.util.Hashtable;
import java.util.Iterator;

public class Server {

	/*
	 * In seconds, used during authentication upon receiving incorrect
	 * credentials 3 times
	 */
	public static int BLOCK_TIME = 60;
	// In seconds, used for checking if the user is online
	public static int LIVE_TIMEOUT = 30;

	// Contains initial store of passwords from credentials.txt
	private Hashtable<String, String> passwords;

	// Maps usernames to records for that user.
	// Record for a particular user is created when he first comes online
	private Hashtable<String, User_Record> Users = new Hashtable<String, User_Record>();

	// Server listening port
	private int port;

	/**
	 * Thread handling the interaction when a message is received from a Client
	 */
	private class ClientHandler extends Thread {
		private Socket socket;
		private ObjectInputStream incoming;
		private ObjectOutputStream outgoing;
		String block_String = ">Your message could not be delivered as the recipient has blocked you";
		String block_String_address = ">Address could not be fetched as the recipient has blocked you";
		String block_broadcast = ">Your message could not be delivered to some recipients";

		ClientHandler(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				System.out.println("Thread spawned to handle incoming message");
				incoming = new ObjectInputStream(socket.getInputStream());
				outgoing = new ObjectOutputStream(socket.getOutputStream());
				// System.out.println(socket.getPort());
				// System.out.println(socket.getLocalPort());
				// System.out.println(socket.getInetAddress());
				// System.out.println(socket.getLocalAddress());

				Message received = (Message) incoming.readObject();
				String sender = received.sender;
				User_Record user = Users.get(sender);
				// System.out.println("Sender name was: "+sender);

				// Read Message Type and act accordingly
				switch (received.getType()) {
				// Only when user is authenticated, any other case?
				case POST:
					InetAddress IP = socket.getInetAddress();
					Message sent = validate_User(sender, received.getValue());
					outgoing.writeObject(sent);

					user = Users.get(sender);
					if (sent.getType().equals(Message.Type.OK)) {
						user.setIP(IP.getHostAddress());
						received = (Message) incoming.readObject();
						user.setPort_number(Integer.valueOf(received.getValue()));
						System.out.println("Server something");
					}
					// System.out.println("Server's User record: "
					// + Users.get(sender).getPort_number());
					// System.out.println("Server's User record: "
					// + Users.get(sender).isAuthenticated);
					// System.out.println("Server's User record: "
					// + Users.get(sender).isOnline);
					// System.out.println("Server's User record: "
					// + Users.get(sender).getIP());

					break;

				case GET:
					// Look-up IP address and port-number of target, send
					// back..
					String fetch_name = received.target;
					if (!fetch_name.equals("ALL")) {
						// getaddress case
						User_Record fetch_record = Users.get(fetch_name);
						if (fetch_record != null) {
							if (!isOnline(fetch_record)) {
								String info = fetch_name
										+ " is currently offline. Address not found";
								outgoing.writeObject(new Message(
										Message.Type.ERROR, info));
							} else if (!fetch_record.getBlacklist().isEmpty()) {
								if (fetch_record.getBlacklist()
										.contains(sender)) {
									outgoing.writeObject(new Message(
											Message.Type.ERROR,
											block_String_address));
								}
							} else {
								String answer = fetch_record.getIP()
										+ " "
										+ Integer.valueOf(fetch_record
												.getPort_number());
								outgoing.writeObject(new Message(
										Message.Type.OK, answer));

							}
						}
					} else {
						// Online users case

					}
					break;

				case MESSAGE:

					String receiver_name = received.target;
					User_Record receiver_record = Users.get(receiver_name);
					if (!isOnline(receiver_record)) {
						String info = receiver_name
								+ " is currently offline. Your message will be "
								+ "delivered when the user comes back online";
						outgoing.writeObject(new Message(Message.Type.ERROR,
								info));
						// TODO Handle offline messaging
					} else if (!receiver_record.getBlacklist().isEmpty()) {
						if (receiver_record.getBlacklist().contains(sender)) {
							outgoing.writeObject(new Message(
									Message.Type.ERROR, block_String));
						}
					} else {
						// Receiver not offline and sender not blocked
						if (send_Message(received, receiver_record)) {
							outgoing.writeObject(new Message(Message.Type.OK));
						} else {
							String something_Wrong = "Message send failed! Reason: Unknown";
							outgoing.writeObject(new Message(
									Message.Type.ERROR, something_Wrong));
						}
					}

					break;

				case BROADCAST:
					// TODO
					// foreach receiver name
					// don't handle offline messaging here..
					break;

				case LIVE:
					// System.out.println("Received a LIVE message from " +
					// sender);
					synchronized (user) {
						user.last_LIVEtime = System.currentTimeMillis();
						user.isOnline = true;
					}
					break;

				case BLOCK:
					// Sender_record
					user = Users.get(sender);
					// Guy yo block
					String to_Block = received.target;
					if (user != null && (user.getBlacklist() != null)) {
						if (received.getValue().equals("1")) {

							user.getBlacklist().add(to_Block);
							outgoing.writeObject(new Message(
									Message.Type.OK));

						} else if (received.getValue().equals("0")) {
							
							user.getBlacklist().remove(to_Block);
							outgoing.writeObject(new Message(
									Message.Type.OK));
						}
					}
					break;

				case LOGOUT:
					// sender and received there
					// TODO Broadcast change in status
					System.out.println("Received a LOGOUT message from "
							+ sender);
					synchronized (Users) {
						Users.remove(user);
					}
					break;

				default:
					break;
				}
				socket.close();
//				System.out.println("Close called");

			} catch (IOException e) {
				System.out.println("In IOException catch block");
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				System.out.println("Error in Message deserialization");
				e.printStackTrace();
			}
		}
	}

	private boolean send_Message(Message message, User_Record recipient) {
		try {
			Socket send_Socket = new Socket(recipient.getIP(),
					recipient.getPort_number());
			ObjectOutputStream outgoing = new ObjectOutputStream(
					send_Socket.getOutputStream());
			ObjectInputStream incoming = new ObjectInputStream(
					send_Socket.getInputStream());
			outgoing.writeObject(message);
			send_Socket.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private synchronized boolean isOnline(User_Record receiver) {
		if (receiver == null) {
			return false;
		}
		if (!receiver.isOnline) {
			return false;
		}
		long since_lastLIVE = System.currentTimeMillis()
				- receiver.last_LIVEtime;
		if (receiver != null && (LIVE_TIMEOUT * 1000 > since_lastLIVE)
				&& receiver.isOnline) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Initialize users by reading credentials from the credentials.txt file
	 */
	private void initialize_Users() {
		String sdir = System.getProperty("user.dir");
		String sep = System.getProperty("file.separator");
		File creds_file = new File(sdir + sep + "credentials.txt");
		try {
			BufferedReader input = new BufferedReader(
					new FileReader(creds_file));
			String line = input.readLine();
			passwords = new Hashtable<String, String>();
			while (line != null) {
				String[] word = line.split(" ");
				passwords.put(word[0], word[1]);
				line = input.readLine();
			}
//			System.out.println(passwords.size());
			input.close();
		} catch (FileNotFoundException e) {
			System.out
					.println("File not found! Ensure credentials.txt is in the base project folder..");
			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	// TODO Offline could be handled here
	public Message validate_User(String username, String password) {
		String welcome = ">Welcome to simple chat server!";
		String invalid = ">Invalid Password. Please try again";
		String last_trial = ">Invalid Password. Your account has been blocked. Please try again after sometime";
		String blocked = ">Due to multiple login failures, your account has been blocked. Please try again after sometime";

		Message result = null;

		User_Record user = Users.get(username);
		// Initialize if the record doesn't exist at all
		if (user == null) {
			user = new User_Record(username);
			Users.put(username, user);
		}

		synchronized (user) {
			if (user.isBlocked) {
				long time_elapsed = System.currentTimeMillis()
						- user.block_Starttime;
				if (BLOCK_TIME * 1000 > time_elapsed) {
					return new Message(Message.Type.ERROR, blocked);
				} else {
					user.isBlocked = false;
					user.tries_left = 3;
				}
			}

			if (password.equals(passwords.get(username))) {
				// Client authenticated, send OK
				user.isAuthenticated = true;
				user.isOnline = true;
				user.tries_left = 3;
				user.last_LIVEtime = System.currentTimeMillis();
				result = new Message(Message.Type.OK, welcome);
			} else {
				--user.tries_left;
				if (user.tries_left == 0) {
					user.isBlocked = true;
					user.block_Starttime = System.currentTimeMillis();
					result = new Message(Message.Type.ERROR, last_trial);
				} else {
					result = new Message(Message.Type.ERROR, invalid);
				}
			}
		}
		return result;
	}

	public Server(int port) {
		initialize_Users();
		this.port = port;
		listen();
	}

	private void listen() {
		try {
			ServerSocket welcomeSocket = new ServerSocket(port);
			while (true) {
				new ClientHandler(welcomeSocket.accept()).start();
			}
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	public static void main(String argv[]) throws Exception {
		int port_num = 0;
		try {
			port_num = Integer.parseInt(argv[0]);
		} catch (Exception e) {
			System.out.println("Invalid port number specified");
			System.out.println("Usage: java Server <port-number>");
		}
		new Server(port_num);

	}
}