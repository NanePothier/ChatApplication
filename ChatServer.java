
import java.awt.BorderLayout;
import java.net.InetAddress;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.net.UnknownHostException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class ChatServer extends JFrame implements Runnable
{
	private JTextArea displayText;
	private static InetAddress[] IPAddresses; //store all IP addresses
	private static String[] clientNames; //store all names of clients
	private static int[] clientPorts; 
	private final int numClients = 50; //can accept 50 clients
	private DatagramSocket serverSocket;
	private ExecutorService runApp;
	private final int portNumber = 12345;
	private String cMessage;
	
	// constructor to set up server
	public ChatServer()
	{
		super("Chat Server"); // set title
		
		runApp = Executors.newCachedThreadPool();
		IPAddresses = new InetAddress[numClients];
		clientNames = new String[numClients];
		clientPorts = new int[numClients];
		
		try{
			serverSocket = new DatagramSocket(portNumber); // set port
		}catch (SocketException socketException){
			socketException.printStackTrace();
			System.exit(1);
		}
		
		displayText = new JTextArea();
		add(displayText, BorderLayout.CENTER);
		displayText.setEditable(false);
		displayText.setText("Server waiting for connection."); // display text
		
		setSize(300,300);
		setVisible(true);	
	}
	
	// broadcast to clients and execute server
	public void startServer()
	{
		//implement a new thread to broadcast
		broadcastToClients();
	
		runApp.execute(this);
	}
		
	public void run()
	{	
		String clientName;
		int index;
		
		// receive packets
		try{
			while(true){
			
				int counter = 0;
			
				byte[] message = new byte[165];
				DatagramPacket receivePacket = new DatagramPacket(message, message.length);
				serverSocket.receive(receivePacket);
			
				for(int x = 0; x < IPAddresses.length; x++){
					if(IPAddresses[x] != null){
					
						//check if IP address of packet received is already known
						if(IPAddresses[x].equals(receivePacket.getAddress())){
							counter++;
					}
				}
			}
			
			//if IP address is not known, then user is new, therefore store user name
			if(counter == 0){
				synchronized(this){
				for(int x = 0; x < IPAddresses.length; x++){
					if(IPAddresses[x] == null){
						IPAddresses[x] = receivePacket.getAddress();
						clientPorts[x] = receivePacket.getPort();
						clientName = new String(receivePacket.getData(), 0, receivePacket.getLength());
						
						while(clientName.contains(" ")){
							index = clientName.indexOf(" ");
							clientName = clientName.substring(0,index) + clientName.substring(index + 1);
						}
						
						if(clientName.startsWith("@")){
							clientName = clientName.substring(1);
						}
						
						if(clientName.length() > 11){
							clientName = clientName.substring(0,10);
						}
						
						clientNames[x] = "@" + clientName;
						sendNameToAll(clientName);
						displayMessage("\n" + clientName + " was stored.");
						break;
					}
				}
				}
			}else{ 
				//if user is already known, proceed with processing message
				processMessage(receivePacket);
			}
		}
		}catch(IOException io){	
			io.printStackTrace();
		}
	}
	
		public void processMessage(DatagramPacket received){		
			//send message received to specified user
			runApp.execute(new Runnable(){
				public void run(){
					
					cMessage = new String(received.getData(), 0, received.getLength());
					
					if(cMessage.startsWith("@")){
								sendPrivateMessage(cMessage, received.getAddress(), received.getPort());	
					}else{
								sendBackToSame("\nMessage was not sent. Enter @, the username, a space and then your message.", received.getAddress(), received.getPort());
				}	
				}		
			});			
		}
	
	// display message on server display
		public void displayMessage(final String message){
				SwingUtilities.invokeLater(
						new Runnable()
						{
							public void run(){
								displayText.append(message);
							}
						}
						);
		}
		
		public void broadcastToClients(){
			//execute new thread, keep broadcasting server address so clients can pick it up
			runApp.execute(new Runnable(){
				
				public void run(){
					
					while(true){
						
						try{
							byte[] broadcast = new byte[100];
							String here = "I am here.";
							broadcast = here.getBytes();
						
							InetAddress group = InetAddress.getByName("239.0.255.0");//address needs to match address specified in client application
							DatagramPacket packet = new DatagramPacket(broadcast, broadcast.length, group, 50001);//50001: number needs to match number in MulticastSocket in ChatClient
							serverSocket.send(packet);
						}catch(UnknownHostException unknown){
							unknown.printStackTrace();
						}catch(IOException ioE){
							ioE.printStackTrace();
						}
						
						try{
							Thread.sleep(3000);
						}catch(InterruptedException interrupted){
							interrupted.printStackTrace();
						}		
					}	
				}
			});
		}
	
		public void sendPrivateMessage(String message, InetAddress sendFrom, int senderPort){
			
			//forward message to specified client
			String name;
			String realMessage;
			int ind;
			boolean matches = false;
			String sender = "Unknown";
			
			if(message.length() < 11){
				message = message + ("           .");
			}
				
			// get the first 11 letters of string
			name = message.substring(0,10);
			
			if(name.contains(" ")){
				ind = name.indexOf(" ");
				name = name.substring(0, (ind - 1));
			}else{
				ind = 11;
			}
			
			realMessage = message.substring(ind);
			
			//find where IP address of sender is stored and then match client name to it
			for(int x = 0; x < IPAddresses.length; x++){
				if(IPAddresses[x].equals(sendFrom)){
					sender = clientNames[x].substring(1);
					break;
				}
			}
			
			synchronized(this){
			for(int x = 0; x < clientNames.length; x++){
				
				if(clientNames[x] != null && clientNames[x].regionMatches(true, 0, name, 0, ind - 1 )){
					
					if(IPAddresses[x] != null){
						
						try{
							//send message to client specified
							String messageToClient = sender + ": " + realMessage;
							byte[]data = messageToClient.getBytes();
						
							DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddresses[x], clientPorts[x]);
							serverSocket.send(sendPacket);
							matches = true;
						}catch(IOException ioException){
							ioException.printStackTrace();
						}	
					}
				}
			}
			}
			
			if(matches == false){
				//message could not be forwarded, notify sender
				sendBackToSame("\nMessage was not sent. User name does not exist.", sendFrom, senderPort);
			}
		}
	
		public void sendNameToAll(String n){
			//notify all chat clients if a new client (user) has entered the chatroom
			runApp.execute(new Runnable(){
				public void run(){
					
					String message = n + " entered the chat room.";
					byte[] data = message.getBytes();
					
					synchronized(this){
					for(int x = 0; x < IPAddresses.length; x++){
						if(IPAddresses[x] != null && clientPorts[x] != 0){
							
							try{	
								DatagramPacket namePacket = new DatagramPacket(data, data.length, IPAddresses[x], clientPorts[x]);
								serverSocket.send(namePacket);
							}catch(IOException ioException){
								ioException.printStackTrace();
							}
						}
					}
					}	
				}		
			});		
	}	
	
	public void sendBackToSame(String message, InetAddress sendTo, int port){
		//send message back to sender
		try{
			byte[] data = message.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, sendTo, port );
			serverSocket.send(sendPacket);
			displayMessage("\nReplied to sender: Message not sent.");
			
		}catch(UnknownHostException e){
			e.printStackTrace();
		}catch(IOException ioException){
			ioException.printStackTrace();
		}
	}	
}// end class server
