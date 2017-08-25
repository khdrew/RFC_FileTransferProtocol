
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import com.google.gson.Gson;

class TCPServer { 
	
	public static final String HOSTNAME = "localhost";
	public static final int PORTNUMBER = 6789;
	
	
	private static String listDir = "";
	public static void main(String argv[]) throws Exception 
	{ 
		UserData[] udArray = null;
		// initialize user data
		BufferedReader reader = new BufferedReader(new FileReader ("userdata.json"));
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();
		String temp = "";

		try {
			while((line = reader.readLine()) != null) {
				stringBuilder.append(line + "\n");
			}
			temp = stringBuilder.toString();
			udArray = new Gson().fromJson(temp, UserData[].class);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			reader.close();
		}
		System.out.println("User Data Loaded...");
		List<UserData> userDataList = Arrays.asList(udArray);
		for (UserData ud : userDataList) {
			System.out.println("user:" + ud.user + ", account:" + ud.account + ", password:" + ud.password);
		}
		
		String clientSentence; 
		String outputSentence = ""; 
		String cmd;
		String rootDir = "root";
		
		ServerSocket welcomeSocket = new ServerSocket(PORTNUMBER); 
		
		while(true) { 
			// set new client settings
			LoginState state = LoginState.WAIT_ACC;
			String user = null;
			String account = null;
			String type = "B";
			String currentDir = rootDir;
			String tempChangeDir = null;
			int tobeNext = 0;
			boolean readyToSend = false;
			byte[] outArray = null;
			File oldFile = null;
			
			Socket connectionSocket = welcomeSocket.accept(); 
			
			BufferedReader inFromClient = 
			new BufferedReader(new
				InputStreamReader(connectionSocket.getInputStream())); 
			
			DataOutputStream  outToClient = 
			new DataOutputStream(connectionSocket.getOutputStream()); 
			
			outToClient.writeBytes("+" + HOSTNAME + " SFTP Service" + '\n'); 
			
			while (true){
				
				clientSentence = inFromClient.readLine(); 
				
				StringTokenizer tokenizedLine =
				new StringTokenizer(clientSentence);
				
				cmd = tokenizedLine.nextToken();
				if (cmd.equals("DONE")){
					outputSentence = "+" + HOSTNAME + " closing connection";
				} else if (readyToSend){
					while (true){
						if (clientSentence.equals("SEND")){
							for (int i = 0; i < outArray.length; i++){
								String s = Integer.toString((int)outArray[i]);
								outToClient.writeBytes(s + '\n');
							}
							outputSentence = "+File Sent";
							break;				
						}else if (clientSentence.equals("STOP")){
							outputSentence = "+ok, RETR aborted";
							break;
						}
						clientSentence = inFromClient.readLine();
					}					
					readyToSend = false;
					
				} else if (!tokenizedLine.hasMoreTokens()){
					outputSentence = "-ERROR: Not enough args...";
					
				} else if (cmd.equals("USER")) { // USER
					if (state == LoginState.WAIT_ACC || state == LoginState.WAIT_PW){
						String tempUser = tokenizedLine.nextToken();
						int result = checkUser(tempUser, userDataList);
						if (result == -1){ // invalid user
							outputSentence = "-Invalid user-id, try again";
							tempChangeDir = null;
						}else if (result == 0){ // bypass acc and password
							outputSentence = "!" + tempUser + " logged in";
							state = LoginState.LOGIN_USER;
							user = tempUser;
							account = null;
							tempChangeDir = null;
						}else if (result == 1){ // request account & password
							outputSentence = "+User-id valid, send account and password";
							user = tempUser;
							account = null;
							state = LoginState.WAIT_ACC;
						}
					}else{
						outputSentence = "-ERROR: Already logged in as " + ((state==LoginState.LOGIN_USER)?user:account);
					}
					
				} else if (cmd.equals("ACCT")) {
					if (state == LoginState.WAIT_ACC || state == LoginState.WAIT_PW){
						String tempAccount = tokenizedLine.nextToken();
						int result = checkAccount(tempAccount, userDataList);
						if (result == -1){ // invalid account
							outputSentence = "-Invalid account, try again";
							tempChangeDir = null;
						}else if (result == 0){ // bypass password
							outputSentence = "!Account valid, logged in";
							state = LoginState.LOGIN_ACCOUNT;
							account = tempAccount;
							user = null;
							if (tempChangeDir != null){
								currentDir = tempChangeDir;
								outputSentence += "\0!Changed working dir to " + currentDir;
								tempChangeDir = null;
							}
						}else if (result == 1){ // request password
							outputSentence = "+Account valid, send password";
							account = tempAccount;
							user = null;
							state = LoginState.WAIT_PW;
						}
					}else{
						outputSentence = "-ERROR: Already logged in as " + ((state==LoginState.LOGIN_USER)?user:account);
					}

				} else if (cmd.equals("PASS")) { // password
					if (state == LoginState.WAIT_PW){ // login for account
						String tempPassword = tokenizedLine.nextToken();
						int result = checkPassAccount(tempPassword, account, userDataList);
						if (result == -1){ // invalid password
							outputSentence = "-Wrong password, try again";
							tempChangeDir = null;
						}else if (result == 1){ // logged in account
							outputSentence = "!Logged in";
							state = LoginState.LOGIN_ACCOUNT;
							if (tempChangeDir != null){
								currentDir = tempChangeDir;
								outputSentence += "\0!Changed working dir to " + currentDir;
								tempChangeDir = null;
							}
						}
					}else if (state == LoginState.WAIT_ACC){ // login for user
						String tempPassword = tokenizedLine.nextToken();
						int result = checkPassUser(tempPassword, user, userDataList);
						if (result == -1){ // invalid password
							outputSentence = "-Wrong password, try again";
							tempChangeDir = null;
						}else if (result == 1){ // logged in user but no account
							outputSentence = "+Send account";
						}
					}else{
						outputSentence = "-ERROR: Already logged in as " + ((state==LoginState.LOGIN_USER)?user:account);
					}
					
				} else if (cmd.equals("TYPE")) {
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){
						String arg = tokenizedLine.nextToken();
						if (arg.equals("A")){
							outputSentence = "+Using Ascii mode";
							type = "A";
						}else if (arg.equals("B")){
							outputSentence = "+Using Binary mode";
							type = "B";
						}else if (arg.equals("C")){
							outputSentence = "+Using Continuous mode";
							type = "C";
						}else{
							outputSentence = "-Type not valid";
						}
					}
				} else if (cmd.equals("LIST")){
					String arg = tokenizedLine.nextToken();
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER) {
						if (arg.equals("F") || arg.equals("V")){
							File subString;
							String dir = "";
							if (!tokenizedLine.hasMoreTokens()){ // CURRENT DIRECTORY
								subString = new File (currentDir);
							}else{ // CHANGE DIRECTORY
								dir = tokenizedLine.nextToken();
								subString = changeDir(dir, currentDir, rootDir);
							}
							if (subString.exists() && getFileExtension(new File(dir)).equals("")){ // check if exists and is a directory
								currentDir = subString.getPath();
								listDir = "\0+" + currentDir + "<CRLF>\0\\.<CRLF>\0\\..";
								getList(subString, subString, arg);
								String[] outputList = listDir.split("<CRLF>");
								for (String s : outputList){
									outToClient.writeBytes(s + '\n');
								}
								outputSentence = "";
//								outputSentence = listDir;
							}else{
								outputSentence = "-ERROR: directory-path does not exist in current directory";
							}
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}

				} else if (cmd.equals("CDIR")){					
					String dir = tokenizedLine.nextToken();
					File subString;
					// CHANGE DIRECTORY
					subString = changeDir(dir, currentDir, rootDir);
					if (subString.exists() && getFileExtension(new File(dir)).equals("")){ // check if exists and is a directory
						if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // if logged in
							currentDir = subString.getPath();
							outputSentence = "!Changed working dir to " + currentDir;
						} else {
							outputSentence = "+directory ok, send account/password";
							tempChangeDir = subString.getPath();
						}
					}else{
						outputSentence = "-ERROR: directory-path does not exist in current directory";
					}
				
				} else if (cmd.equals("KILL")) {
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){
						String dir = tokenizedLine.nextToken();
						File subString = new File(currentDir, dir);
						try{ // attempt to delete file
							if (subString.exists()){
								boolean result = subString.delete();
								if (result){
									outputSentence = "+" + dir + " deleted";
								}else{
									outputSentence = "-Not deleted, no permissions";
								}
							}else{
								outputSentence = "-Not deleted, file does not exist";
							}
						}catch(Exception e){}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
					
				} else if (cmd.equals("NAME")){
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){
						String dir = tokenizedLine.nextToken();
						File fileDir = new File(currentDir, dir);
						if (!fileDir.exists() || !fileDir.isFile()){
							outputSentence = "-Can't find " + dir + " file";
						}else{
							outputSentence = "+File exists, enter new name with TOBE";
							oldFile = fileDir;
							tobeNext = 1; // expect tobe
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
				
				} else if (cmd.equals("TOBE")){
					if (tobeNext == 2 && oldFile != null){ // expecting tobe
						String newFileName = tokenizedLine.nextToken();
						File newFile = new File(currentDir, newFileName);
						if(oldFile.renameTo(newFile)){
							outputSentence = "+" + oldFile.getPath() + " renamed to " + newFile.getPath();
						}else{
							outputSentence = "-File wasn't renamed because no permissions or invalid name";
						}
					
					}else{
						outputSentence = "-ERROR: out of order commands";
					}
					
				} else if (cmd.equals("RETR")){
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){
						String dir = tokenizedLine.nextToken();
						File targetFile = (new File(currentDir,dir));
						if (targetFile.exists() && targetFile.isFile()){							
							outArray = extractBytes(targetFile.getPath());							
							outputSentence = Integer.toString(outArray.length);
							readyToSend = true;
						}else{
							outputSentence = "-File doesn't exist";
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
					
				} else { // UNRECOGNISED COMMAND
					outputSentence = "-ERROR: Unrecognised Command...";
				}
				
				if (tobeNext == 1){ // reset tobe, but bypass initial tobe set
					tobeNext = 2;
				}else{
					tobeNext = 0;
				}

				outToClient.writeBytes(outputSentence + '\n'); 
				if (cmd.equals("DONE")){
					break;
				}
			}
		} 
	}

	private static int checkPassAccount(String password, String account, List<UserData> userDataList){
		if (account == null || password == null){
			return -1;
		}
		for (UserData ud : userDataList){ // check password
			if (ud.account != null){
				if (ud.account.equals(account) && ud.password.equals(password)){
					return 1;
				}
			}
		}		
		return -1;
	}

	private static String getFileExtension(File file) {
		String fileName = file.getName();
		if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
			return fileName.substring(fileName.lastIndexOf(".")+1);
		else return "";
	}
	
	
	private static int checkPassUser(String password, String user, List<UserData> userDataList){
		if (user == null || password == null){
			return -1;
		}
		for (UserData ud : userDataList){ // check if password is correct
			if (ud.user != null){
				if (ud.user.equals(user) && ud.password.equals(password)){
					return 1;
				}
			}
		}		
		return -1;
	}
	
	private static File changeDir(String dir, String currentDir, String rootDir){
		File subString;
		
		if (dir.equals("..")){
			if(currentDir.lastIndexOf("\\") != -1 && currentDir.lastIndexOf("\\") != 0){
				subString = new File(currentDir.substring(0,currentDir.lastIndexOf("\\")+1));
			} else if(currentDir.lastIndexOf("/") != -1 && currentDir.lastIndexOf("/") != 0){
				subString = new File(currentDir.substring(0,currentDir.lastIndexOf("/")+1));
			}else{
				subString = new File(currentDir);
			}
		}else if (dir.contains(".")){
			subString = new File(currentDir);
		}else{
			subString = new File(currentDir, dir);
		}
		System.out.println(subString);
		return subString;
	}
	
	public static byte[] extractBytes (String ImageName) throws IOException 
	{
	    Path path = Paths.get(ImageName);
	    byte[] data = Files.readAllBytes(path);
	    return data;
	}
	
	private static int checkUser(String name, List<UserData> userDataList){
		if (name.equals(HOSTNAME)){
			return 0;
		}
		
		for (UserData ud : userDataList) {
			if (ud.user != null){
				if (ud.user.equals(name)){ // found user
					if (ud.account == null && ud.password == null){ // check if has no password and account
						return 0;
					}else{
						return 1;
					}
				}
			}
		}
		return -1;
	}
	
	
	
	private static int checkAccount(String name, List<UserData> userDataList){

		for (UserData ud : userDataList) {
			if (ud.account != null){
				if (ud.account.equals(name)){ // found account
					if (ud.password == null){ // check if has password
						return 0;
					}else{
						return 1;
					}
				}
			}
		}
		return -1;
	}
	
	public static void getList(File node, File parentNode, String mode){
//		String subString = node.getPath().substring(parentNode.getPath().length());
//		System.out.println("LIST");
		
		File[] f = node.listFiles();
		if (f != null){
			for (File ft : f){
				String subString = ft.getName();
				if (!subString.equals("")){
					int length = 31 - subString.length();
					listDir += "<CRLF>\0" + subString;
					for (int i = 0; i < length; i++){
						listDir += ' ';
					}
					

					if (mode.equals("V")){
						if(node.exists()){
							double bytes = ft.length();
							if (getFileExtension(ft) != ""){
								listDir += " | " + String.format("%15s", bytes) + " bytes";
							}else{
								listDir += " | " + String.format("%21s", "");
							}
							Date date = new Date(node.lastModified());
							DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
							String dataFormatted = format.format(date);
							
							listDir += " | " + String.format("%13s", (node.canWrite()?"not-protected":"protected"));
							listDir += " | " + String.format("%20s", dataFormatted);
							
						}
					}
				}
				
				
				
				
			}
		}
	
		
	}
} 

class UserData {
	UserData(){}
	public String user;
	public String account;
	public String password;
}
