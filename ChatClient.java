
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.net.UnknownHostException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.MulticastSocket;

public class ChatClient extends JFrame implements Runnable
{
	private JTextField enterField;
	private JTextArea displayField;
	private JPanel panel;
	private JButton btnSend;
	private ExecutorService clientThreads;
	private JScrollPane pane;
	private DatagramSocket clientSocket;
	private MulticastSocket multiSocket;
	private InetAddress serverAddress;
	
	public ChatClient(){
		
		setTitle("Chat Room");
		
		panel = new JPanel(new BorderLayout(10,10));
		panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		
		//client threads
		clientThreads = Executors.newCachedThreadPool();
		
		displayField = new JTextArea(4000,20);
		displayField.setPreferredSize(new Dimension(400, 100));
		displayField.setEditable(false);
		displayField.setWrapStyleWord(true);
		displayField.setLineWrap(true);
		displayField.setText("Welcome to the chat room.\n\nBefore sending a message:\nEnter your name above (10 letters max.) and hit Send.\n\nWhen sending a message, enter @username, a space and then your message.\n\n");
		
		pane = new JScrollPane(displayField);
		
		enterField = new JTextField();
		enterField.setText("");
		enterField.setEditable(true);
		enterField.setPreferredSize(new Dimension(400, 100));
		
			
		add(panel, BorderLayout.CENTER);
		panel.add(enterField,BorderLayout.NORTH);
		panel.add(pane, BorderLayout.CENTER);
			
		btnSend = new JButton("Send");
		panel.add(btnSend, BorderLayout.SOUTH);
			
		setSize(350, 350);
		setVisible(true);
		
		startClient();	
	}
	
	public void startClient(){
	
		// open socket and multicast socket for client
		try{
			clientSocket = new DatagramSocket();
			multiSocket = new MulticastSocket(50001);
			InetAddress group = InetAddress.getByName("239.0.255.0");//address needs to match address specified in server
			multiSocket.joinGroup(group);
			
		}catch(SocketException socketException){
			socketException.printStackTrace();
			System.exit(1);
		}catch(IOException ioEx){
			ioEx.printStackTrace();
		}
		
		while(true){
			// listen for broadcast packets from server
			try{
				byte[] b = new byte[100];
				DatagramPacket packet = new DatagramPacket(b, b.length);
				multiSocket.receive(packet);
				serverAddress = packet.getAddress();
			}catch(IOException ioE){
				ioE.printStackTrace();
			}
		
			if(serverAddress != null){
				displayMessage("Server address received");
				break;
			}
		}
		
		//execute client thread
		clientThreads.execute(this);
	}
	
	public void run()
	{	
		//send message to server when send button is clicked
		btnSend.addActionListener(
				new ActionListener(){
					
					public void actionPerformed( ActionEvent e){
							
						try{
							if(enterField.getText() != ""){
								String m = enterField.getText();
								sendData(m);
							}
								
							enterField.setText("");
						}catch(NullPointerException n){
							n.printStackTrace();
						}
					}
				}	
				);
		
		//continuously listen for incoming messages
		while(true){
			receiveMessage();
		}
	}
	
	private void sendData(String message){
		//send message to server
		try{
			byte[] data = message.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, serverAddress, 12345 ); //12345: number needs to match port server is listening on
			clientSocket.send(sendPacket);
			
		}catch(UnknownHostException e){
			e.printStackTrace();
		}catch(IOException ioException){
			ioException.printStackTrace();
		}
	}
		
	
	public void receiveMessage(){
		
		//receive message from other user (through server)
		try{
			byte [] message = new byte[165];
			DatagramPacket receivePacket = new DatagramPacket(message, message.length);
			
			clientSocket.receive(receivePacket);
			
			String clientM = new String(receivePacket.getData(), 0, receivePacket.getLength());
			String toDisplay = "\n" + clientM;
			
			displayMessage(toDisplay);
			
		}catch(IOException ioException){
			ioException.printStackTrace();
		}
	}
	
	private void displayMessage(final String message){
		//display messsage of other user in textfield
		SwingUtilities.invokeLater(
				new Runnable()
				{
					public void run(){
						displayField.append(message);
					}
				}
				);
	}	
}//end class
