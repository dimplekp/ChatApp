package chatapp;

import java.net.*;
import java.io.*;
import java.util.*;

public class Client  {

	private ObjectInputStream sInput;		// to read from the socket
	private ObjectOutputStream sOutput;		// to write on the socket
	private Socket socket;

	private ClientGUI clientGUIobj;
	
	private String server, username;
	private int port;

	/*
	 *  Constructor called by console mode
	 *  server: the server address
	 */
	Client(String server, int port, String username) {
		// calls the other constructor
		this(server, port, username, null);
	}

	/*
	 * Constructor call when used from a GUI
	 * in console mode the ClienGUI parameter is null
	 */
	Client(String server, int port, String username, ClientGUI clientGUIobj) {
		this.server = server;
		this.port = port;
		this.username = username;
		// save if we are in GUI mode or not
		this.clientGUIobj = clientGUIobj;
	}
	
	/*
	 * To start the dialog
	 */
	public boolean start() {
		// try to connect to the server
		try {
			socket = new Socket(server, port);
		} 
		catch(Exception ec) {
			display("Error connectiong to the server:" + ec);
			return false;
		}
		
		String msg = "Connection accepted " + socket.getInetAddress() + " : " + socket.getPort();
		display(msg);
	
		// Create Data Streams for both input and output 
		try
		{
			sInput  = new ObjectInputStream(socket.getInputStream());
			sOutput = new ObjectOutputStream(socket.getOutputStream());
		}
		catch (IOException eIO) {
			display("Exception creating new Input/output Streams: " + eIO);
			return false;
		}

		// creates the Thread to listen from the server 
		new ListenFromServer().start();
		// Send username to the server 
		// this is the only message that we will be sent of type String
		// All other messages will be objects of ChatMessage
		try
		{
			sOutput.writeObject(username);
		}
		catch (IOException eIO) {
			display("Exception occured while loging in : " + eIO);
			disconnect();
			return false;
		}
		// successfully connected
		return true;
	}

	/*
	 * To send a message to the console or the GUI
	 */
	private void display(String msg) {
		if(clientGUIobj == null)
			System.out.println(msg);      // print in console mode
		else
			clientGUIobj.append(msg + "\n");		// append to the ClientGUI JTextArea
	}
	
	/*
	 * To send a message to the server
	 */
	void sendMessage(ChatMessage msg) {
		try {
			sOutput.writeObject(msg);
		}
		catch(IOException e) {
			display("Exception writing to server: " + e);
		}
	}

	// When something goes wrong, close the Input/Output streams and disconnect
	private void disconnect() {
		try { 
			if(sInput != null) sInput.close();
		}
		catch(Exception e) {}
		try {
			if(sOutput != null) sOutput.close();
		}
		catch(Exception e) {}
        try{
			if(socket != null) socket.close();
		}
		catch(Exception e) {}
		
		// inform the GUI
		if(clientGUIobj != null)
			clientGUIobj.connectionFailed();
			
	}
	/*
	 * To start the Client in console mode use one of the following command
	 * > java Client
	 * > java Client username
	 * > java Client username portNumber
	 * > java Client username portNumber serverAddress
	 * at the console prompt
	 * If the portNumber is not specified 1500 is used
	 * If the serverAddress is not specified "localHost" is used
	 * If the username is not specified "Anonymous" is used
	 * > java Client 
	 * is equivalent to
	 * > java Client Anonymous 1500 localhost 
	 * 
	 * In console mode, if an error occurs the program simply stops
	 * when a GUI id used, the GUI is informed of the disconnection
	 */
	public static void main(String[] args) {
		// default values
		int portNumber = 1500;
		String serverAddress = "localhost";
		String userName = "Anonymous";

		// depending on the number of arguments provided
		switch(args.length) {
			// > javac Client username portNumber serverAddr
			case 3:
				serverAddress = args[2];
			// > javac Client username portNumber
			case 2:
				try {
					portNumber = Integer.parseInt(args[1]);
				}
				catch(Exception e) {
					System.out.println("Invalid port number.");
					System.out.println("Usage is: > java Client [username] [portNumber] [serverAddress]");
					return;
				}
			// > javac Client username
			case 1: 
				userName = args[0];
			// > java Client
			case 0:
				break;
			// invalid number of arguments
			default:
				System.out.println("Usage is: > java Client [username] [portNumber] {serverAddress]");
			return;
		}
		
		Client client = new Client(serverAddress, portNumber, userName);
		// Test if connection with server can be established
		// If it fails, do nothing
		if(!client.start())
			return;
		
		// wait for messages from user
		Scanner scan = new Scanner(System.in);
		// loop forever for message from the user
		// So that messages can keep coming
		while(true) {
			System.out.print("> ");
			// read message from user
			String msg = scan.nextLine();
			// logout if message is LOGOUT
			if(msg.equalsIgnoreCase("LOGOUT")) {
				client.sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
				// break to disconnect
				break;
			}
			// message WhoIsIn
			else if(msg.equalsIgnoreCase("WHOISIN")) {
				client.sendMessage(new ChatMessage(ChatMessage.WHOISIN, ""));				
			}
			else {				
				// send the message that's been typed
				client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
			}
		}
		// disconnect the client when the control breaks out of the loop
		// It happens when user logs out
		client.disconnect();	
	}

	// A class that waits for the message from the server and append them to the JTextArea
	class ListenFromServer extends Thread {

		public void run() {
			while(true) {
				try {
					String msg = (String) sInput.readObject();
					// if console mode prints the message and adds the prompt back
					if(clientGUIobj == null) {
						System.out.println(msg);
						System.out.print("> ");
					}
					else {
						clientGUIobj.append(msg);
					}
				}
				catch(IOException e) {
					display("Server has closed the connection: " + e);
					if(clientGUIobj != null) 
						clientGUIobj.connectionFailed();
					break;
				}
				
				catch(ClassNotFoundException e2) {
				}
			}
		}
	}
}