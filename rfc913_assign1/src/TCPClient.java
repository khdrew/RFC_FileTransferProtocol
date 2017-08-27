import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
			StringTokenizer tokenizedLine =	new StringTokenizer(sentence);
			temp = tokenizedLine.nextToken();
			if (temp.equals("RETR") && tokenizedLine.hasMoreTokens()){
				System.out.println(inputSentence);
				if (!inputSentence.contains("-")){
					String fileName = tokenizedLine.nextToken();
					while (true){
						sentence = inFromUser.readLine().replaceAll(" ", "");
						if (sentence.equals("STOP")){
							outToServer.writeBytes(sentence + '\n');
							System.out.println(inFromServer.readLine());
							break;
						}else if (sentence.equals("SEND")){
							outToServer.writeBytes(sentence + '\n');
							int byteCount = Integer.parseInt(inputSentence);
							ArrayList<Byte> byteList = new ArrayList<Byte>();
							while(byteCount > 0){
								inputSentence = inFromServer.readLine();
								byteList.add((byte) (Integer.parseInt(inputSentence) & 0x00FF));
								byteCount--;
							}
							System.out.println(inFromServer.readLine());
							saveFile(byteList, clientRoot, fileName);
							break;
						}
					}
				}

			}else if (temp.equals("STOR") && tokenizedLine.hasMoreTokens()){
				System.out.println(inputSentence);
				
				String mode = tokenizedLine.nextToken();
				if (tokenizedLine.hasMoreTokens()){
					String fileName = tokenizedLine.nextToken();
					File targetFile = new File(clientRoot, fileName);
					if (!inputSentence.contains("-") && inputSentence.contains("+")){
						// SENDING SIZE
						byte[] outArray = extractBytes(targetFile.getPath());
						sentence = "SIZE " + outArray.length;
						outToServer.writeBytes(sentence + '\n');
						System.out.println("CLIENT SENDING: " + sentence);
						inputSentence = inFromServer.readLine();
						if (!inputSentence.contains("-") && inputSentence.contains("+")){
							for (int i = 0; i < outArray.length; i++){
								String s = Integer.toString((int)outArray[i]);
								outToServer.writeBytes(s + '\n');
							}
							System.out.println(inFromServer.readLine());
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
	
	
	
	public static void saveFile(ArrayList<Byte> byteList, String clientRoot, String fileName) throws IOException{
		FileOutputStream stream = new FileOutputStream(new File(clientRoot,fileName).getPath());
		try{
			byte[] byteArray = new byte[byteList.size()];
			for(int i = 0; i < byteList.size(); i++) {
				byteArray[i] = byteList.get(i).byteValue();
			}
			stream.write(byteArray);
		} finally {
		    stream.close();
		}		
	}
	
	
	public static byte[] extractBytes (String ImageName) throws IOException {
	    Path path = Paths.get(ImageName);
	    byte[] data = Files.readAllBytes(path);
	    return data;
	}
	
} 
