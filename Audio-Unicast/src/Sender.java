import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;

//import java.nio.ByteOrder;
import javax.sound.sampled.*;

public class Sender {
	private InetAddress Receiver_Address;
	private DatagramSocket Socket = null;
	ByteArrayOutputStream byteArrayOutputStream;
	AudioFormat audioFormat;
	TargetDataLine targetDataLine;

	Sender(InetAddress address) {
		this.Receiver_Address = address;
		audioFormat = getAudioFormat();
		DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class,
				audioFormat);
		try {
			targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
			targetDataLine.open(audioFormat);
			targetDataLine.start();
			Socket = new DatagramSocket();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			System.out.println("Port is probably already in use!");
			e.printStackTrace();
		}
		new SendThread().start();
	}

	class SendThread extends Thread {
		// An arbitrary-size temporary holding
		// buffer
		byte tempBuffer[] = new byte[8192];
		byte[] compressedBuffer = new byte[4096];
		ByteArrayOutputStream ulawOutStream = new ByteArrayOutputStream();
		int len;

		public void run() {
			try {
				while (true) {
					// Read data from the internal
					// buffer of the data line.
					int cnt = targetDataLine.read(tempBuffer, 0,
							tempBuffer.length);
					if (cnt > 0) {
						// Compress and send
						InputStream byteArrayInputStream = new ByteArrayInputStream(
								tempBuffer);
						CompressInputStream ulawInputStream = new CompressInputStream(
								byteArrayInputStream, false);
						len = ulawInputStream.read(compressedBuffer);
						DatagramPacket sendPacket = new DatagramPacket(
								compressedBuffer, compressedBuffer.length,
								Receiver_Address, 50000);
						Socket.send(sendPacket);
					}// end if
				}// end while
					// byteArrayOutputStream.close();
			} catch (Exception e) {
				e.printStackTrace();
				// System.exit(0);
			}// end catch
		}// end run
	}

	private AudioFormat getAudioFormat() {
		float sampleRate = 8000.0F;
		// 8000,11025,16000,22050,44100
		int sampleSizeInBits = 16;
		// 8,16
		int channels = 1;
		// 1,2
		boolean signed = true;
		// true,false
		boolean bigEndian = false;
		// true,false
		// if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
		// bigEndian = true;
		// } else {
		// bigEndian = false;
		// }
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed,
				bigEndian);
	}

	public static void main(String args[]) {
		try {
			new Sender(InetAddress.getByName(args[0]));
		} catch (UnknownHostException e) {
			System.out.println("Enter valid Receiver IP Address or hostname!!");
			System.out.println("Usage: java Sender <Receiver IP-Address>");
			e.printStackTrace();
		}
	}

}