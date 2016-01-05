package chatapp;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server {
	// a unique ID for each connection
	private static int uniqueId;
	// an ArrayList to keep the list of the Client
	private ArrayList<ClientThread> clientList;
	// if I am in a GUI
	private ServerGUI serverGUIobj;
	// to display time
	private SimpleDateFormat simpleDateFormatObj;
	// the port number to listen for connection
	private int port;
	// the boolean that will be turned of to stop the server
	private boolean keepGoing;
	

	// Server constructor that receive the port number to listen to in console
	public Server(int port) {
		this(port, null);
	}
	
	public Server(int port, ServerGUI serverGUIobj) {
		this.serverGUIobj = serverGUIobj;
		this.port = port;
		// to display time
		simpleDateFormatObj = new SimpleDateFormat("HH:mm:ss");
		// ArrayList for the list of Clients
		clientList = new ArrayList<ClientThread>();
	}
	
	public void start() {
		keepGoing = true;
		// create socket server and wait for connection requests
		try 
		{
			// the socket used by the server
			ServerSocket serverSocket = new ServerSocket(port);

			// loop infinitely to wait for connections
			while(keepGoing) 
			{
				display("Server waiting for Clients on port " + port + ".");
				
				Socket socket = serverSocket.accept();  	// accept connection
				// if asked to stop
				if(!keepGoing)
					break;
				ClientThread t = new ClientThread(socket);  // make a thread of the client
				clientList.add(t);	// save the thread in the ArrayList
				t.start();
			}
			// When asked to stop
			try {
				serverSocket.close();
				for(int i = 0; i < clientList.size(); ++i) {
					ClientThread tc = clientList.get(i);
					try {
					tc.sInput.close();
					tc.sOutput.close();
					tc.socket.close();
					}
					catch(IOException ioE) {
					}
				}
			}
			catch(Exception e) {
				display("Exception occured while closing the server and clients: " + e);
			}
		}
		// if something goes wrong while connecting the server and while waiting
		catch (IOException e) {
            String msg = simpleDateFormatObj.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
			display(msg);
		}
	}		
   	// For GUI to stop the server
	protected void stop() {
		keepGoing = false;
		try {
			new Socket("localhost", port);
		}
		catch(Exception e) {
		}
	}
	// Display an event (not a message) to the console or the GUI
	private void display(String msg) {
		String time = simpleDateFormatObj.format(new Date()) + " " + msg;
		if(serverGUIobj == null)
			System.out.println(time);
		else
			serverGUIobj.appendEvent(time + "\n");
	}
	
	// To broadcast a message to all Clients
	private synchronized void broadcast(String message) {
		String time = simpleDateFormatObj.format(new Date());
		String messageLf = time + " " + message + "\n";
		
		if(serverGUIobj == null)
			System.out.print(messageLf);
		else
			serverGUIobj.appendRoom(messageLf);     // append in the room window
		
		// loop in reverse order in case we have to remove a Client when it has disconnected
		for(int i = clientList.size(); --i >= 0;) {
			ClientThread clientThreadObj = clientList.get(i);
			
			// try to write to the Client
			if(!clientThreadObj.writeMsg(messageLf)) {
				// if it fails, remove it from the list
				clientList.remove(i);
				display(clientThreadObj.username + " disconnected.");
			}
		}
	}

	// for a client who logs out using the LOGOUT message
	synchronized void remove(int id) {
		// scan the client thread list to find the ID of the client
		for(int i = 0; i < clientList.size(); ++i) {
			ClientThread clientThreadObj = clientList.get(i);
			// if found
			if(clientThreadObj.id == id) {
				clientList.remove(i);
				return;
			}
		}
	}
	
	/*
	 *  To run as a console application
	 * > java Server
	 * > java Server portNumber
	 * If the port number is not specified, 1500 is used
	 */ 
	public static void main(String[] args) {
		// start server on port 1500 unless a PortNumber is specified 
		int portNumber = 1500;
		switch(args.length) {
			case 1:
				try {
					portNumber = Integer.parseInt(args[0]);
				}
				catch(Exception e) {
					System.out.println("Invalid port number.");
					System.out.println("Usage is: > java Server [portNumber]");
					return;
				}
			case 0:
				break;
			default:
				System.out.println("Usage is: > java Server [portNumber]");
				return;
				
		}
		// create a server object and start it
		Server server = new Server(portNumber);
		server.start();
	}

	// One instance of this thread will run for each client
	class ClientThread extends Thread {
		// the socket to listen or to talk
		Socket socket;
		ObjectInputStream sInput;
		ObjectOutputStream sOutput;
		int id;
		String username;
		ChatMessage chatMsg;
		String date;

		ClientThread(Socket socket) {
			id = ++uniqueId;
			this.socket = socket;
			System.out.println("Thread to create Object Input/Output Streams");
			try
			{
				// create output data stream first
				sOutput = new ObjectOutputStream(socket.getOutputStream());
				sInput  = new ObjectInputStream(socket.getInputStream());
				
				// read the username
				username = (String) sInput.readObject();
				display(username + " just connected.");
			}
			catch (IOException e) {
				display("Exception occured while creating Input/output Streams: " + e);
				return;
			}
			catch (ClassNotFoundException e) {
			}
            date = new Date().toString() + "\n";
		}

		// Runs forever
		public void run() {
			boolean keepGoing = true;
			// loop until LOGOUT
			while(keepGoing) {
				// read a String (which is an object)
				try {
					chatMsg = (ChatMessage) sInput.readObject();
				}
				catch (IOException e) {
					display(username + " left." + e);
					break;				
				}
				catch(ClassNotFoundException e2) {
					break;
				}

				String message = chatMsg.getMessage();

				switch(chatMsg.getType()) {
				case ChatMessage.MESSAGE:
					broadcast(username + ": " + message);
					break;
				case ChatMessage.LOGOUT:
					display(username + " disconnected.");
					keepGoing = false;
					break;
				case ChatMessage.WHOISIN:
					writeMsg("List of the users connected at " + simpleDateFormatObj.format(new Date()) + "\n");
					for(int i = 0; i < clientList.size(); ++i) {
						ClientThread clientThreadObj = clientList.get(i);
						writeMsg((i+1) + ") " + clientThreadObj.username + " since " + clientThreadObj.date);
					}
					break;
				}
			}

			// If loop breaks when client logs out
			// Remove the client from the ArrayList of connected clients
			remove(id);
			close();
		}
		
		private void close() {
			// close the connection
			try {
				if(sOutput != null) sOutput.close();
			}
			catch(Exception e) {}
			try {
				if(sInput != null) sInput.close();
			}
			catch(Exception e) {};
			try {
				if(socket != null) socket.close();
			}
			catch (Exception e) {}
		}

		private boolean writeMsg(String msg) {
			if(!socket.isConnected()) {
				close();
				return false;
			}
			// Write a msg to the Client output stream
			try {
				sOutput.writeObject(msg);
			}
			// If an error occurs, do not abort 
			// Inform the user
			catch(IOException e) {
				display("Error sending message to " + username);
				display(e.toString());
			}
			return true;
		}
	}
}

