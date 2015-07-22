import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.sound.sampled.*;

public class Receiver {
	private DatagramSocket Socket = null;
	private Queue<byte[]> samples_queue = new LinkedList<byte[]>();
	AudioInputStream audioInputStream;
	SourceDataLine sourceDataLine;

	Receiver() {
		try {
			Socket = new DatagramSocket(50000);
			AudioFormat audioFormat = getAudioFormat();
			DataLine.Info dataLineInfo = new DataLine.Info(
					SourceDataLine.class, audioFormat);
			sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
			sourceDataLine.open(audioFormat);

		} catch (SocketException e) {
			System.out.println("Port is probably already in use!");
			e.printStackTrace();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
		sourceDataLine.start();
		new ReceiveThread().start();
		new PlayThread().start();
	}

	private class ReceiveThread extends Thread {
		byte[] receiveData = new byte[4096];
		byte[] decompressBuffer = new byte[8192];
		boolean notify = false;
		int len;

		public void run() {
			while (true) {
				try {
					DatagramPacket receivePacket = new DatagramPacket(
							receiveData, receiveData.length);
					Socket.receive(receivePacket);
					DecompressInputStream pcmInputStream = new DecompressInputStream(
							new ByteArrayInputStream(receivePacket.getData()),
							false);
					len = pcmInputStream.read(decompressBuffer);
					samples_queue.add(decompressBuffer);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class PlayThread extends Thread {
		byte audioData[];

		public void run() {
			while (true) {
				if (samples_queue.isEmpty()) {
					try {
						System.out.println("No audio to be played, sleeping..");
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					audioData = samples_queue.remove();
					sourceDataLine.write(audioData, 0, audioData.length);
				}
			}
		}
	}

	private AudioFormat getAudioFormat() {
		float sampleRate = 8000.0F;
		// 8000,11025,16000,22050,44100
		int sampleSizeInBits = 16;
		int channels = 1;
		boolean signed = true;
		boolean bigEndian = false;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed,
				bigEndian);
	}// end getAudioFormat

	public static void main(String args[]) {
		new Receiver();
	}
}