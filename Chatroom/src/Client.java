import java.io.*;
import java.net.*;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class Client {

	private boolean chatting = false;
	private String name, password;
	int tries_left = 3;
	private ServerSocket my_listenSocket;
	private int my_port;
	private int server_port;
	private InetAddress server_address;
	private Hashtable<String, User_Record> friends;
	public static int LIVE_INTERVAL = 30;

	/**
	 * 
	 * Listens for messages(sent through message or broadcast command) sent to
	 * the Client asynchronously
	 * 
	 */
	private class Listener extends Thread {
		private ServerSocket listener;

		Listener(ServerSocket socket) {
			this.listener = socket;
		}

		public void run() {
			try {
				System.out.println("Client listening on: "
						+ listener.getLocalPort());
				while (chatting) {
					new Receiver(listener.accept()).start();
					System.out.println("Still listening.... " + chatting);
				}
			} catch (SocketException s) {
				System.out.println("Listener closed");
			} catch (IOException e) {

				e.printStackTrace();
			}
		}
	}

	/**
	 * 
	 * Takes received messages(sent through message or broadcast command) and
	 * displays them on the terminal
	 *
	 */
	private class Receiver extends Thread {
		private Socket socket;
		ObjectInputStream incoming;
		ObjectOutputStream outgoing;

		Receiver(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				// System.out.println("In Receiver, someone tried to connect");
				incoming = new ObjectInputStream(socket.getInputStream());
				outgoing = new ObjectOutputStream(socket.getOutputStream());
				Message received = (Message) incoming.readObject();
				String sender = received.sender;
				if (received.getType() == Message.Type.LOGOUT) {
					chatting = false;
				}
				if (sender.equals("Server") && (received.getValue() != null)) {
					System.out.println(">" + received.getValue());
				} else {
					System.out.println(">" + sender + ": "
							+ received.getValue());
				}
				socket.close();
			} catch (IOException e) {

				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				System.out.println("Error in Message deserialization");
				e.printStackTrace();
			}
		}
	}

	public Client(InetAddress server_address, int server_port) {
		this.server_port = server_port;
		this.server_address = server_address;
		friends = new Hashtable<String, User_Record>();
		if (get_authentication()) {
			System.out.println("User was authenticated");
			start_Chat();
		} else {
			System.out.println("Exiting...");
		}
	}

	private boolean get_authentication() {
		boolean is_authenticated = false;
		String username, password;

		Scanner input = new Scanner(System.in);
		System.out.print(">Username: ");
		username = input.next();
		this.name = username;

		try {
			while (tries_left > 0) {
				System.out.print(">Password: ");
				password = input.next();
				this.password = password;
				Socket auth_Socket = new Socket(server_address, server_port);
				ObjectOutputStream outgoing = new ObjectOutputStream(
						auth_Socket.getOutputStream());
				ObjectInputStream incoming = new ObjectInputStream(
						auth_Socket.getInputStream());

				Message auth = new Message(Message.Type.POST, password);
				auth.sender = username;
				outgoing.writeObject(auth);

				Message result = (Message) incoming.readObject();
				System.out.println(result.getValue());

				if (result.getType() == Message.Type.OK) {
					// Start to listen and send port-number
					is_authenticated = true;
					my_listenSocket = new ServerSocket(0);
					my_port = my_listenSocket.getLocalPort();
					System.out.println("Started server socket");
					// Inform Server of port number
					Message inform_port = new Message(Message.Type.POST,
							String.valueOf(my_port));
					inform_port.sender = username;
					outgoing.writeObject(inform_port);
					auth_Socket.close();

					tries_left = 3;
					// Start Listening
					new Listener(my_listenSocket).start();
					break;
				} else {
					if (result.getType() == Message.Type.ERROR) {
						--tries_left;
						auth_Socket.close();
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Could not connect to Server!!");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {

			e.printStackTrace();
		}

		// input.close();
		return is_authenticated;
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

	// Sending LIVE messages at every LIVE_INTERVAL
	private class Heartbeat extends TimerTask {
		public void run() {
			// System.out.println("Heartbeating");
			try {
				Message heartbeat = new Message(Message.Type.LIVE);
				heartbeat.sender = name;
				Socket socket = new Socket(server_address, server_port);
				ObjectOutputStream outgoing = new ObjectOutputStream(
						socket.getOutputStream());
				ObjectInputStream incoming = new ObjectInputStream(
						socket.getInputStream());
				outgoing.writeObject(heartbeat);

				socket.close();

			} catch (IOException e) {

				e.printStackTrace();
			}
		}
	}

	// Where interaction from the user is handled and commands sent
	private void start_Chat() {
		// boolean will be used to log client off, maybe unsynchronizedly
		chatting = true;
		Scanner chat_scanner = new Scanner(System.in);
		int index;
		String command, input, param;
		Socket client_Socket;
		Message to_Send, received;
		Timer timer = new Timer();
		timer.schedule(new Heartbeat(), 0, LIVE_INTERVAL * 1000);
		// BufferedReader input_reader = new BufferedReader(new
		// InputStreamReader(System.in));

		while (chatting) {
			System.out.print(">");
			try {
				// input = input_reader.readLine();
				if (chat_scanner.hasNextLine()) {
					input = chat_scanner.nextLine().trim();
					// System.out.println("Read a line!");
					if ((!input.startsWith(" ")) && input.contains(" ")) {
						index = input.indexOf(' ');
						command = input.substring(0, index);
						param = input.substring(index + 1);
					} else {
						command = input;
						param = null;
					}

					// -----------------------

					// Read command and act accordingly
					if ((command.equals("message") || command.equals("private"))
							&& !param.isEmpty()) {
						index = param.indexOf(' ');
						String receiver = param.substring(0, index);
						String message = param.substring(index + 1);
						to_Send = new Message(Message.Type.MESSAGE, message);
						to_Send.sender = name;
						to_Send.target = receiver;

						if (command.equals("message")) {
							// Non-direct case: Routing message through Server

							client_Socket = new Socket(server_address, server_port);
							ObjectOutputStream outgoing = new ObjectOutputStream(
									client_Socket.getOutputStream());
							ObjectInputStream incoming = new ObjectInputStream(
									client_Socket.getInputStream());
							
							outgoing.writeObject(to_Send);
							received = (Message) incoming.readObject();
							client_Socket.close();
							if (received.getType() == Message.Type.ERROR) {
								System.out.println(received.getValue());
							}
						} else {
							// Direct case: Sending directly to recipient
//							client_Socket.close();

							// If empty, ask for getaddress
							// else, get values from table and send
							User_Record friend_record = friends.get(receiver);
							if (friend_record == null) {
								System.out
										.println("Receiver address not found, use getaddress <receiver>");
							} else if (friend_record.getIP() == null) {
								System.out
										.println("Receiver address not found, receiver might be offline");
							} else {
								String peer_IP = friend_record.getIP();
								int peer_port = friend_record.getPort_number();
								
								Socket peer_Socket = new Socket(peer_IP,
										peer_port);
								ObjectOutputStream outgoing_peer = new ObjectOutputStream(
										peer_Socket.getOutputStream());
								ObjectInputStream incoming_peer = new ObjectInputStream(
										peer_Socket.getInputStream());
								outgoing_peer.writeObject(to_Send);
								peer_Socket.close();
							}
							// peer_Socket things
						}
					} else if (command.equals("broadcast") && !param.isEmpty()) {
						to_Send = new Message(Message.Type.MESSAGE, param);
						to_Send.sender = name;
						to_Send.target = "ALL";

						client_Socket = new Socket(server_address, server_port);
						ObjectOutputStream outgoing = new ObjectOutputStream(
								client_Socket.getOutputStream());
						ObjectInputStream incoming = new ObjectInputStream(
								client_Socket.getInputStream());
						outgoing.writeObject(to_Send);
						received = (Message) incoming.readObject();
						client_Socket.close();
						if (received.getType() == Message.Type.ERROR) {
							System.out.println(received.getValue());
						}
					} else if (command.equals("getaddress") && !param.isEmpty()) {
						to_Send = new Message(Message.Type.GET, param);
						to_Send.sender = name;
						to_Send.target = param;

						client_Socket = new Socket(server_address, server_port);
						ObjectOutputStream outgoing = new ObjectOutputStream(
								client_Socket.getOutputStream());
						ObjectInputStream incoming = new ObjectInputStream(
								client_Socket.getInputStream());
						outgoing.writeObject(to_Send);
						received = (Message) incoming.readObject();
						client_Socket.close();
						
						if (received.getType() == Message.Type.ERROR) {
							System.out.println(received.getValue());
						} else if (received.getType() == Message.Type.OK) {
							String address = received.getValue();
							System.out.println("Received address " + address
									+ " for user " + param);
							index = address.indexOf(' ');
							String IP_add = address.substring(0, index);
							String pport = address.substring(index + 1);
							User_Record friend_record = friends.get(param);
							// Initialize if the record doesn't exist at all
							if (friend_record == null) {
								friend_record = new User_Record(param);
								friends.put(param, friend_record);
							}
							friend_record.setIP(IP_add);
							friend_record
									.setPort_number(Integer.valueOf(pport));
						}
					} else if (command.equals("block")) {
						to_Send = new Message(Message.Type.BLOCK, "1");
						to_Send.sender = name;
						to_Send.target = param;

						client_Socket = new Socket(server_address, server_port);
						ObjectOutputStream outgoing = new ObjectOutputStream(
								client_Socket.getOutputStream());
						ObjectInputStream incoming = new ObjectInputStream(
								client_Socket.getInputStream());
						outgoing.writeObject(to_Send);
						received = (Message) incoming.readObject();
						client_Socket.close();
						if (received.getType() == Message.Type.OK) {
							System.out.println("User " + param
									+ " was successfully blocked");
						}
					} else if (command.equals("unblock")) {
						to_Send = new Message(Message.Type.BLOCK, "0");
						to_Send.sender = name;
						to_Send.target = param;

						client_Socket = new Socket(server_address, server_port);
						ObjectOutputStream outgoing = new ObjectOutputStream(
								client_Socket.getOutputStream());
						ObjectInputStream incoming = new ObjectInputStream(
								client_Socket.getInputStream());
						outgoing.writeObject(to_Send);
						received = (Message) incoming.readObject();
						client_Socket.close();
						if (received.getType() == Message.Type.OK) {
							System.out.println("User " + param
									+ " was successfully un-blocked");
						}
					} else if (command.equals("online")) {

					} else if (command.equals("logout")) {
						to_Send = new Message(Message.Type.LOGOUT);
						to_Send.sender = name;

						client_Socket = new Socket(server_address, server_port);
						ObjectOutputStream outgoing = new ObjectOutputStream(
								client_Socket.getOutputStream());
						ObjectInputStream incoming = new ObjectInputStream(
								client_Socket.getInputStream());
						outgoing.writeObject(to_Send);
						client_Socket.close();
						chatting = false;
					} else {
						System.out.println("Invalid Command!!");
					}
					// ......Commands processed.......

				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				System.out.println("Error in Message deserialization");
				e.printStackTrace();
			}
		}

		try {
			my_listenSocket.close();
		} catch (IOException e) {
			System.out.println("Error closing listen Socket");
			e.printStackTrace();
		}
		timer.cancel();
		System.out.println("Exiting...");
		// chat_scanner.close();
	}

	public static void main(String argv[]) throws Exception {
		int port = 0;
		InetAddress address = null;

		try {
			address = InetAddress.getByName(argv[0]);
			port = Integer.parseInt(argv[1]);
			System.out.println("Connecting to IP: " + address + " and port: "
					+ port);
		} catch (NumberFormatException e) {
			System.out.println("Enter a valid server port number");
			System.out
					.println("Usage: java Client <Server IP-Address> <Server port-number>");
			e.printStackTrace();
		} catch (UnknownHostException e) {
			System.out.println("Enter valid Server IP Address or hostname");
			System.out
					.println("Usage: java Client <Server IP-Address> <Server port-number>");
			e.printStackTrace();
		}

		new Client(address, port);

	}
}