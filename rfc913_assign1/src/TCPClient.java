import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.StringTokenizer; 
class TCPClient { 
	
	public static final String HOSTNAME = "localhost";
	public static final int PORTNUMBER = 6789;
	
	public static void main(String argv[]) throws Exception 
	{ 
		String sentence; 
		String inputSentence;
		String cmd;
		boolean running = true;
		BufferedReader inFromUser = 
		new BufferedReader(new InputStreamReader(System.in)); 
		
		System.out.println("Requesting connection to " + HOSTNAME + " server...");
		
		Socket clientSocket = new Socket(HOSTNAME, PORTNUMBER); 
					
		DataOutputStream outToServer = 
		new DataOutputStream(clientSocket.getOutputStream()); 
		
		
		BufferedReader inFromServer = 
		new BufferedReader(new
			InputStreamReader(clientSocket.getInputStream())); 
		
		inputSentence = inFromServer.readLine(); 
		System.out.println(inputSentence);
		
		String clientRoot = "clientRoot";
		String temp;
		
		while (true){
			sentence = inFromUser.readLine(); 
							
			outToServer.writeBytes(sentence + '\n');
			
			
			inputSentence = inFromServer.readLine(); 
			StringTokenizer tokenizedLine =
					new StringTokenizer(sentence);
			temp = tokenizedLine.nextToken();
			if (temp.equals("RETR") && tokenizedLine.hasMoreTokens()){
				System.out.println(inputSentence);
				if (!inputSentence.contains("-")){
					String fileName = tokenizedLine.nextToken();
					while (true){
						sentence = inFromUser.readLine();
						outToServer.writeBytes(sentence + '\n');
						if (sentence.equals("STOP")){
							break;
						}else if (sentence.equals("SEND")){
							int byteCount = Integer.parseInt(inputSentence);
							FileWriter fw = new FileWriter(new File(clientRoot, fileName));
							BufferedWriter bw = new BufferedWriter(fw);
							String content = "";
							while(byteCount > 1){
								inputSentence = inFromServer.readLine();
								if (inputSentence.equals("<CRLF>")){
									content += "\n";
								}else{
									content += inputSentence;
								}								
							}
							bw.write(content);
							System.out.println("Filed saved");
						}
					}
				}
				
			}else if (inputSentence.contains("\0")){
				while (inputSentence.contains("\0")){
					System.out.println(inputSentence.replaceAll("\0",""));
					inputSentence = inFromServer.readLine(); 
				}
			}else{
				System.out.println(inputSentence); 
			}
			
			if (inputSentence.equals("+" + HOSTNAME + " closing connection")){
				System.out.println("Closing connection...");
				clientSocket.close();
				break;
			}
		}			
		
			
	} 
} 
