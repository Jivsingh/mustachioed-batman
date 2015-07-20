import java.io.*;
import java.net.*;

import java.util.Random;

class pingpong {
	
	public pingpong(float initial_wait, InetAddress address) throws IOException{
		new pingpongThread(address, initial_wait*1000).start();
	}

	public static void main(String args[]) throws Exception {
		Random rand = new Random();
		float randomStart = rand.nextFloat()*3f + 2;
		System.out.println("Random Start Value used = "+randomStart+"\n");
		
		InetAddress IPAddress = InetAddress.getByName(args[1]);
		System.out.println("Sending to IP Address: "+IPAddress.getHostAddress());
		new pingpong(randomStart, IPAddress);
		
	}
}