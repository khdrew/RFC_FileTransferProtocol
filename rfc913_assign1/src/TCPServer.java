
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;

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

				}else if (!tokenizedLine.hasMoreTokens()){
					outputSentence = "-ERROR: Not enough args...";
					
				} else if (cmd.equals("USER")) { // USER
					if (state == LoginState.WAIT_ACC || state == LoginState.WAIT_PW){
						String tempUser = tokenizedLine.nextToken();
						int result = checkUser(tempUser, userDataList);
						if (result == -1){ // invalid user
							outputSentence = "-Invalid user-id, try again";
						}else if (result == 0){ // bypass acc and password
							outputSentence = "!" + tempUser + " logged in";
							state = LoginState.LOGIN_USER;
							user = tempUser;
							account = null;
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
						}else if (result == 0){ // bypass password
							outputSentence = "!Account valid, logged in";
							state = LoginState.LOGIN_ACCOUNT;
							account = tempAccount;
							user = null;
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
						}else if (result == 1){ // logged in account
							outputSentence = "!Logged in";
							state = LoginState.LOGIN_ACCOUNT;
						}
					}else if (state == LoginState.WAIT_ACC){ // login for user
						String tempPassword = tokenizedLine.nextToken();
						int result = checkPassUser(tempPassword, user, userDataList);
						if (result == -1){ // invalid password
							outputSentence = "-Wrong password, try again";
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
							String dir;
							if (!tokenizedLine.hasMoreTokens()){
								subString = new File (currentDir);
							}else{
								dir = tokenizedLine.nextToken();
								subString = new File(currentDir, dir);
							}
							if (subString.exists() && getFileExtension(subString).equals("")){
								currentDir = subString.getPath();
								listDir = "+" + currentDir + "\0\\.\0\\..";
								getList(subString, subString, arg);
								outputSentence = listDir;
							}else{
								outputSentence = "-ERROR: directory-path does not exist in current directory";
							}
						}
					}else{
						outputSentence = "-ERROR: Not logged in, try logging in";
					}

				} else if (cmd.equals("CDIR")){					
					String dir = tokenizedLine.nextToken();
					File subString = new File (currentDir, dir);
					if (subString.exists()){
						if (getFileExtension(subString).equals("")){
							if (state == LoginState.LOGIN_ACCOUNT || state == LoginState.LOGIN_USER) {
								outputSentence = "!Changed working dir to " + dir;
								currentDir = subString.getPath();
							}else{
								outputSentence = "+directory ok, send account/password";
							}
						}else{
							outputSentence = "-ERROR: Not a directory";
						}
					}
					

				} else {
					outputSentence = "-ERROR: Unrecognised Command...";
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
		String subString = node.getPath().substring(parentNode.getPath().length());
		System.out.println(subString);
		if (!subString.equals("")){
			listDir += "\0" + subString;

			if (mode.equals("V")){
				if(node.exists()){
					double bytes = node.length();
					Date date = new Date(node.lastModified());
					DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
					String dataFormatted = format.format(date);
					listDir += "\t ~ \t" + bytes + " bytes" + "\t ~ \t" + (node.canWrite()?"not-protected":"protected") + "\t ~ \t" + dataFormatted;
				}
			}
		}
		
		if (node.isDirectory()){
			String[] subNote = node.list();
			for (String filename : subNote){
				getList(new File(node, filename), parentNode, mode);
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
