// CS725 - RFC913
// KLAI054 - 6747578
// SERVER

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
import java.util.List;
import java.util.StringTokenizer;

import com.google.gson.Gson;

class ServerMain { 
	
	public static final String HOSTNAME = "localhost";
	public static final int PORTNUMBER = 6789;
	
	private static String listDir = "";
	public static void main(String argv[]) throws Exception
	{ 
		// initialize user data
		List<UserData> userDataList = loadUserData("userdata.json");		
		
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
			String password = null;
			String type = "B";
			String currentDir = rootDir;
			String tempChangeDir = null; // user for when CDIR is done without being logged in
			int tobeNext = 0; // rename flag
			File oldFile = null; // rename file
			
			boolean readyToSend = false; // sending files with RETR
			byte[] outArray = null; // file to be sent out
			
			int awaitSize = 0; // STOR flag
			File writeFile = null;
			
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
				// DONE COMMAND - close connection to client
				if (cmd.equals("DONE")){ // close connection
					outputSentence = "+" + HOSTNAME + " closing connection";
					
				// RETR COMMAND - await for SEND or STOP command to start or stop sending the requested file
				} else if (readyToSend){ 
					while (true){
						if (clientSentence.equals("SEND")){ // send out bytes to client
							for (int i = 0; i < outArray.length; i++){
								String s = Integer.toString((int)outArray[i]);
								outToClient.writeBytes(s + '\n');
							}
							outputSentence = "+File Sent";
							break;				
						}else if (clientSentence.equals("STOP")){ // stop sending
							outputSentence = "+ok, RETR aborted";
							break;
						}
						clientSentence = inFromClient.readLine();
					}					
					readyToSend = false;
					
				// STOR COMMAND - await for SIZE and receive file to be stored
				} else if (awaitSize > 0){
					while (true){
						if (cmd.equals("SIZE") && tokenizedLine.hasMoreTokens()){
							int size = Integer.parseInt(tokenizedLine.nextToken());
							// accept file
							outToClient.writeBytes("+ok, waiting for file" + '\n'); 
							ArrayList<Byte> byteList = new ArrayList<Byte>();
							while(size > 0){
								byteList.add((byte) (Integer.parseInt(inFromClient.readLine()) & 0x00FF));
								size--;
							}
							
							// storing file
							byte[] byteArray = null; // array to be written to file 
							if (awaitSize == 1){ // create new file array
								byteArray = new byte[byteList.size()];
								for(int i = 0; i < byteList.size(); i++) {
									byteArray[i] = byteList.get(i).byteValue();
								}
							}else{ // append file array
								byte[] existingFile = extractBytes(writeFile.getPath());
								byteArray = new byte[existingFile.length + byteList.size()];
								for (int i = 0; i < existingFile.length; i++){
									byteArray[i] = existingFile[i];
								}
								for(int i = 0; i < byteList.size(); i++) {
									byteArray[i + existingFile.length] = byteList.get(i).byteValue();
								}
							}
							FileOutputStream stream = new FileOutputStream(writeFile.getPath());
							try{ // try save file
								stream.write(byteArray);
								outputSentence = "+Saved " + writeFile.getPath();
								awaitSize = 0;
							}catch (Exception e){ // could not write to file
								outputSentence = "-Couldn't write to " + writeFile.getPath();
							} finally {
							    stream.close();
							}
							break;
						}
						clientSentence = inFromClient.readLine(); 
						tokenizedLine = new StringTokenizer(clientSentence);
						cmd = tokenizedLine.nextToken();
					}
				
				// ERROR COMMAND - other commands that do not have enough arguments to operate	
				} else if (!tokenizedLine.hasMoreTokens()){
					outputSentence = "-ERROR: Not enough args...";
				
				// USER COMMAND - adding user to server, or log in
				} else if (cmd.equals("USER")) { 
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
							password = null;
							if (tempChangeDir != null){ // change directory if requested prior to log in
								currentDir = tempChangeDir;
								outputSentence += " Changed working dir to " + currentDir;
								tempChangeDir = null;
							}
						}else if (result == 1){ // request account & password
							outputSentence = "+User-id valid, send account and password";
							user = tempUser;
							account = null;
							password = null;
							state = LoginState.WAIT_ACC;
						}
					}else{
						outputSentence = "-ERROR: Already logged in as " + ((state==LoginState.LOGIN_USER)?user:account);
					}
				
				// ACCT COMMAND - adding account to system, or log in
				} else if (cmd.equals("ACCT")) {
					if (state == LoginState.WAIT_ACC || state == LoginState.WAIT_PW){
						String tempAccount = tokenizedLine.nextToken();
						int result = checkAccount(tempAccount, userDataList, password);
						if (result == -1){ // invalid account
							outputSentence = "-Invalid account or password, try again";
							tempChangeDir = null;
							password = null;
						}else if (result == 0){ // bypass password
							outputSentence = "!Account valid, logged in";
							state = LoginState.LOGIN_ACCOUNT;
							account = tempAccount;
							user = null;
							if (tempChangeDir != null){ // change directory if requested prior to log in
								currentDir = tempChangeDir;
								outputSentence += " Changed working dir to " + currentDir;
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
				
				// PASS COMMAND - adding password to system, or log in
				} else if (cmd.equals("PASS")) { // password
					if (state == LoginState.WAIT_PW){ // login for ACCOUNT
						String tempPassword = tokenizedLine.nextToken();
						int result = checkPassAccount(tempPassword, account, userDataList);
						if (result == -1){ // invalid password
							outputSentence = "-Wrong password, try again";
							tempChangeDir = null;
						}else if (result == 1){ // logged in account
							outputSentence = "!Logged in";
							state = LoginState.LOGIN_ACCOUNT;
							if (tempChangeDir != null){ // change directory if requested prior to log in
								currentDir = tempChangeDir;
								outputSentence += " Changed working dir to " + currentDir;
								tempChangeDir = null;
							}
						}
					}else if (state == LoginState.WAIT_ACC){ // login for USER
						String tempPassword = tokenizedLine.nextToken();
						int result = checkPassUser(tempPassword, user, userDataList);
						if (result == -1){ // invalid password
							outputSentence = "-Wrong password, try again";
							tempChangeDir = null;
						}else if (result == 1){ // logged in user but no account
							outputSentence = "+Send account";
							password = tempPassword;
						}
					}else{
						outputSentence = "-ERROR: Already logged in as " + ((state==LoginState.LOGIN_USER)?user:account);
					}
					
				// TYPE COMMAND - sets transmission mode
				} else if (cmd.equals("TYPE")) { // setting type
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // check if logged in
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
					
				// LIST COMMAND - lists files and directories in current working directory
				} else if (cmd.equals("LIST")){
					String arg = tokenizedLine.nextToken();
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER) { // check if logged in
						if (arg.equals("F") || arg.equals("V")){ // check if mode is correct
							File subString;
							String dir = "";
							if (!tokenizedLine.hasMoreTokens()){ // parse current directory
								subString = new File (currentDir);
							}else{ // parse input directory
								dir = tokenizedLine.nextToken();
								subString = changeDir(dir, currentDir, rootDir);
							}
							if (subString.exists() && getFileExtension(new File(dir)).equals("")){ // check if exists and is a directory
								currentDir = subString.getPath();
								listDir = "\0+" + currentDir + "<CRLF>\0\\.<CRLF>\0\\..";
								getList(subString, subString, arg);
								String[] outputList = listDir.split("<CRLF>");
								for (String s : outputList){ // get list of files and directories and send
									outToClient.writeBytes(s + '\n');
								}
								outputSentence = "";
							}else{
								outputSentence = "-ERROR: directory-path does not exist in current directory";
							}
						}else{
							outputSentence = "-ERROR: Incorrect mode specification";
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
				
				// CDIR COMMAND - changes working directory to specified directory
				} else if (cmd.equals("CDIR")){					
					String dir = tokenizedLine.nextToken();
					File subString = changeDir(dir, currentDir, rootDir);
					if (subString.exists() && getFileExtension(new File(dir)).equals("")){ // check if exists and is a directory
						if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // if logged in
							currentDir = subString.getPath();
							outputSentence = "!Changed working dir to " + currentDir;
						} else { // currently not logged in, save for next log in attempt
							outputSentence = "+directory ok, send account/password";
							tempChangeDir = subString.getPath();
						}
					}else{
						outputSentence = "-ERROR: directory-path does not exist in current directory";
					}
				
				// KILL COMMAND - deletes specified file
				} else if (cmd.equals("KILL")) {
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // check if logged in
						String dir = tokenizedLine.nextToken();
						File subString = new File(currentDir, dir);
						try{ // attempt to delete file
							if (subString.exists()){ // check if file exists
								boolean result = subString.delete();
								if (result){ // check permissions
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
				
				// NAME COMMAND - renames specified file
				} else if (cmd.equals("NAME")){
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // check if logged in
						String dir = tokenizedLine.nextToken();
						File fileDir = new File(currentDir, dir);
						if (!fileDir.exists() || !fileDir.isFile()){ // check if specified item exists and is a file
							outputSentence = "-Can't find " + dir + " file";
						}else{
							outputSentence = "+File exists, enter new name with TOBE";
							oldFile = fileDir;
							tobeNext = 1; // expect TOBE
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
				
				// TOBE COMMAND - follow up from NAME COMMMAND
				} else if (cmd.equals("TOBE")){
					if (tobeNext == 2 && oldFile != null){ // expecting TOBE
						String newFileName = tokenizedLine.nextToken();
						File newFile = new File(currentDir, newFileName);
						if(oldFile.renameTo(newFile)){ // check if rename-able
							outputSentence = "+" + oldFile.getPath() + " renamed to " + newFile.getPath();
						}else{
							outputSentence = "-File wasn't renamed because no permissions or invalid name";
						}
					
					}else{
						outputSentence = "-ERROR: out of order commands";
					}
					
				// RETR COMMAND - retrieve file
				} else if (cmd.equals("RETR")){
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // check if logged in
						String dir = tokenizedLine.nextToken();
						File targetFile = (new File(currentDir,dir));
						if (targetFile.exists() && targetFile.isFile()){ // check if file exists and is actually a file					
							outArray = extractBytes(targetFile.getPath());							
							outputSentence = Integer.toString(outArray.length);
							readyToSend = true; // initialize RETR loop
						}else{
							outputSentence = "-File doesn't exist";
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
					
				// STOR COMMAND - client send a file to be stored in server root
				} else if (cmd.equals("STOR")){
					if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER){ // check if logged in
						String mode = tokenizedLine.nextToken();
						if (tokenizedLine.hasMoreTokens()){
							String fileName = tokenizedLine.nextToken();
							File targetFile = new File(currentDir, fileName);
							writeFile = targetFile;
							boolean exists = targetFile.exists(); // checking if file exists
							awaitSize = 0;
							if (mode.equals("NEW")){ // for each mode, set out message and mode
								outputSentence = (exists)?"+File exists, will create new generation of file":
									"+File does not exist, will create new file";
								awaitSize = 1;
							} else if (mode.equals("OLD")){
								outputSentence = (exists)?"+Will write over old file":"+Will create new file";
								awaitSize = 1;
							} else if (mode.equals("APP")){
								outputSentence = (exists)?"+Will append to file":"+Will create file";
								awaitSize = ((exists)?2:1);
							} else{
								outputSentence = "-ERROR: Incorrect mode specification";
							}
						}else{
							outputSentence = "-ERROR: Not enough args...";
						}
						
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}
					
				} else { // UNRECOGNISED COMMAND
					outputSentence = "-ERROR: Unrecognised Command...";
				}
				
				if (tobeNext == 1){ // reset TOBE, but bypass initial TOBE set
					tobeNext = 2;
				}else{
					tobeNext = 0;
				}

				// SEND TO CLIENT
				outToClient.writeBytes(outputSentence + '\n'); 
				
				if (cmd.equals("DONE")){ // CLOSE connection to client if DONE is the command
					break;
				}
			}
		} 
	}

	// checks if password and/or account exists and is correct in the user data list
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
		// 1 for existing account/password, -1 for error
	}

	// checks if password and/or user exists and is correct in the user data list
	private static int checkPassUser(String password, String user, List<UserData> userDataList){
		if (password == null){
			return -1;
		}else if (user == null){
			return 1;
		}
		for (UserData ud : userDataList){ // check if password is correct
			if (ud.user != null){
				if (ud.user.equals(user) && ud.password.equals(password)){
					return 1;
				}
			}
		}		
		return -1;
		// 1 for existing user/password, -1 for error
	}
	
	// check user exists
	@SuppressWarnings("unused")
	private static int checkUser(String name, List<UserData> userDataList){
		if (name.equals(HOSTNAME)){
			return 0;
		}
		if (name == null){
			return 1;
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
		// -1 for error, 1 requires account/password, 0 does not need account/password
	}
	
	// check if account exists	
	private static int checkAccount(String name, List<UserData> userDataList, String password){		
		for (UserData ud : userDataList) {
			if (ud.account != null){
				if (ud.account.equals(name)){ // found account
					if (password == null){ // check if has no password input before
						if (ud.password == null){ // account does not require password
							return 0; // bypass and log in
						}else{
							return 1; // does require password
						}
					}else{ // user has placed password in before
						if (ud.password == null){ // if password is null, then previous password is wrong
							return -1;
						}else if (ud.password.equals(password)){
							return 0; // logged in
						}else{
							return -1; // incorrect password
						}
					}
				}
			}
		}
		return -1;
		// -1 error, 1 require password, 0 account and password match
	}
	
	
	// set directory change based on current directory, root and input directory
	private static File changeDir(String dir, String currentDir, String rootDir){
		File subString;		
		if (dir.equals("..")){ // set back one directory
			if(currentDir.lastIndexOf("\\") != -1 && currentDir.lastIndexOf("\\") != 0){
				subString = new File(currentDir.substring(0,currentDir.lastIndexOf("\\")+1));
			} else if(currentDir.lastIndexOf("/") != -1 && currentDir.lastIndexOf("/") != 0){
				subString = new File(currentDir.substring(0,currentDir.lastIndexOf("/")+1));
			}else{
				subString = new File(currentDir);
			}
		}else if (dir.contains(".")){ // set current directory as current
			subString = new File(currentDir);
		}else{ // set new directory
			subString = new File(currentDir, dir);
		}
		return subString;
	}
	
	// gets the file extension of a given file name 
	private static String getFileExtension(File file) {
		String fileName = file.getName();
		if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
			return fileName.substring(fileName.lastIndexOf(".")+1);
		else return "";
	}

	// extracts file bytes
	public static byte[] extractBytes (String ImageName) throws IOException {
	    Path path = Paths.get(ImageName);
	    byte[] data = Files.readAllBytes(path);
	    return data;
	}
	
	// gets listing of files to be printed
	public static void getList(File node, File parentNode, String mode){
		File[] f = node.listFiles();
		if (f != null){
			for (File ft : f){ // list all current directory files
				String subString = ft.getName();
				if (!subString.equals("")){
					int length = 31 - subString.length();
					listDir += "<CRLF>\0" + subString;
					for (int i = 0; i < length; i++){
						listDir += ' ';
					}
					if (mode.equals("V")){ // verbose listings
						if(node.exists()){
							double bytes = ft.length();
							if (getFileExtension(ft) != ""){
								listDir += " | " + String.format("%15s", bytes) + " bytes"; // size
							}else{
								listDir += " | " + String.format("%21s", ""); // directory, pad with spaces
							}
							// protected file/directory listing
							listDir += " | " + String.format("%13s", (node.canWrite()?"not-protected":"protected"));
							
							// last modified listing
							Date date = new Date(node.lastModified());
							DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
							String dataFormatted = format.format(date);
							listDir += " | " + String.format("%20s", dataFormatted);
							
						}
					}
				}
			}
		}		
	}
	
	// loading all user data function from JSON file
	private static List<UserData> loadUserData(String filePath) throws IOException{
		UserData[] udArray = null;
		List<UserData> userDataList;
		
		BufferedReader reader = new BufferedReader(new FileReader (filePath));
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
		
		// print users
		System.out.println("User Data Loaded...");
		userDataList = Arrays.asList(udArray);
		for (UserData ud : userDataList) {
			System.out.println("user:" + ud.user + ", account:" + ud.account + ", password:" + ud.password);
		}
		return userDataList;
	}

} 

// JSON data type
class UserData {
	UserData(){}
	public String user;
	public String account;
	public String password;
}
