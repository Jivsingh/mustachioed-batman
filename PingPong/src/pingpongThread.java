import java.io.IOException;
import java.io.StringReader;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.json.*;

public class pingpongThread extends Thread {

	private InetAddress address;
	private DatagramSocket Socket = null;
	// private int counter = 0;
	private float start_timer;

	public pingpongThread(InetAddress address, float start_timer) throws IOException {
		this.address = address;
		this.start_timer = start_timer;
		Socket = new DatagramSocket(50000);
		Socket.setSoTimeout((int) start_timer);

	}

	private DatagramPacket poll() throws IOException {
		byte[] receiveData = new byte[1024];
		DatagramPacket receivePacket = new DatagramPacket(receiveData,
				receiveData.length);
		try {

			Socket.receive(receivePacket);

		} catch (SocketTimeoutException st) {

			System.out.println("Socket timeout occured after "
					+ (start_timer / 1000) + " seconds, initiating ping-pong");
			send_pkt(1);

			return poll();
		}
		return receivePacket;
	}

	private void send_pkt(int counter) throws IOException {

		JsonObject sent_JSON = get_JSON(counter);
		System.out.println("Putting current timestamp and sending out the JSON Object: \n"
						+ sent_JSON);
		// byte[] sendData = ByteBuffer.allocate(4).putInt(counter).array();

		byte[] sendData = sent_JSON.toString().getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, address, 50000);
		Socket.send(sendPacket);
		System.out.println("Counter value sent = " + counter + "\n");

	}

	private JsonObject get_JSON(int counter) {

		Date full_Date = new Date();
		String date = new SimpleDateFormat("MM/dd/yyyy").format(full_Date);
		String time = new SimpleDateFormat("HH:mm:ss.S").format(full_Date);
		JsonBuilderFactory factory = Json.createBuilderFactory(null);
		JsonObject JSON = factory.createObjectBuilder()
				.add("counter", counter).add("timestamp",
						factory.createObjectBuilder()
						.add("date", date)
						.add("time", time)).build();
		return JSON;

	}

	private void doPingPong(DatagramPacket receivedPacket)
			throws InterruptedException, IOException {

		// Integer received =
		// ByteBuffer.wrap(receivedPacket.getData()).getInt();
		Date full_Date = new Date();
		String date = new SimpleDateFormat("MM/dd/yyyy").format(full_Date);
		String time = new SimpleDateFormat("HH:mm:ss.S").format(full_Date);
		System.out.println("Package received at date - " + date
				+ " and time - " + time);

		JsonObject received_JSON = Json.createReader(
				new StringReader(new String(receivedPacket.getData())))
				.readObject();
		InetAddress IPAddress = receivedPacket.getAddress();
		int port = receivedPacket.getPort();
		int received_counter = received_JSON.getInt("counter");
		System.out.println("Received IP address and port no.: " + IPAddress
				+ " and " + port);
		System.out.println("Counter value received = " + received_counter);
		System.out.println("JSON Object received = \n" + received_JSON);

		System.out.println("\nGoing to sleep for 800ms now..\n");
		sleep(800);
		received_counter++;

		System.out.println("Incrementing counter and sending new JSON");
		send_pkt(received_counter);

	}

	public void run() {
		while (true) {
			try {
				DatagramPacket receivedPacket = poll();
				doPingPong(receivedPacket);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				System.out.println("Someone interrupted my sleep!!");
				e.printStackTrace();

			}
		}
	}
}
