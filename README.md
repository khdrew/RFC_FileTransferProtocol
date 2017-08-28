# COMPSYS 725 ASSIGNMENT 1

RFC913 - Server and Client - Andrew Lai - klai054 - 6747578

## Compile and Running

1. Extract project file `rfc913_assign1` to desktop
2. Open up Eclipse Java EE and import the project file
3. Using the project explorer open up `ServerMain.java` (found in `src` `default package`)
4. Run `ServerMain.java`
5. Open up `ClientMain.java` and run it as well.
6. Input commands into the console running `ClientMain.java`
7. **all input commands are case sensitive**

## File System

Local server files are located in the project folder named as `root` directory while the client files are located in `clientRoot` directory. All files retrieved by client from the server will be stored in the `clientRoot`, files sent from client comes from `clientRoot` to the`root` file.

## User Data

User data is stored as a `.json` file as a make shift database `userdata.json`. User, accounts and passwords are as follows:


| User  | Account | Password |
| ----- | ------- | -------- |
| admin | N/A     | N/A      |
| u1    | a1      | p1       |
| u2    | N/A     | N/A      |
| N/A   | a3      | p3       |
| N/A   | a4      | N/A      |

Entries with N/A are entries that are not required and can bypass entries of values for their respective user/account.

Upon starting the server, the user data is loaded in and printed in the console.

## USER, ACCT, PASS and DONE

With server and client open, enter into the client console the following:

- `USER admin` or `USER u2`  - will log in the server without any account and password being specified

Closing the client connection can be done with input of `DONE` in the client console. This will allow you to log in again. Run `ClientMain.java` again to request another connection to the server. Make sure closing the connection is done with this command, not with a force stop unless the server is restarted as well.

- Input `USER u1`, `ACCT a1` and `PASS p1` to log into `u1/a1`.
- Reset the connection again with `DONE`  and input the following in any order `ACCT a3` and `PASS p3`, this will log into `a3`.
- `DONE` again and log in with only just `ACCT a4`.



## Testing CDIR LIST and TYPE

Once logged in, the default directory will be `root`. The client user cannot back out of the `root` directory. 

Manually check the directory by checking the project file. The contents of the root file should contain:

- `test` directory
  - `123.txt` text file
- `d1` directory
  - `dir` directory
    - `fire4.txt` text file
    - `fire5.txt` text file
  - `file1.txt` text file
  - `file2.txt` text file
  - `file3.txt` text file
- `bnw_uoa_logo.jpg` image file
- `test.txt` text file
- `uni.png` image file
- `killme.txt` text file
- `nameme.txt` text file

### Test Cases

Using the `LIST` command will list the immediate directory while adding a directory name after the command will change the directory and list the files. 

- Input command `LIST V` to show verbose listings of the current directory, or `LIST F` to show non-verbose listing of the current directory
- `LIST F test` will list files in and change the working directory into `test`
- Using `CDIR ..` will change the directory to the previous directory which currently would be the `root` directory
- `LIST V d1/dir` will verbose list the files found in the directory `dir` found in `d1`.
- `CDIR . ` will keep the current working directory
- `TYPE A` or `TYPE B` or `TYPE C` to change the mode

If a change directory was requested while not logged in, upon logging in the working directory  will change. To test this with:

- Request a new connection, `DONE`
- Request change of directory while not logged in `CDIR test`
- Log in as admin using `USER admin` and the directory will change upon logging in

If one user log in was incorrect, the directory change will not go through.

- Request new connection, `DONE`
- Request change of directory while not logged in `CDIR test`
- Fail a log in using `USER abc`
- Log in as admin using `USER admin` and the directory will NOT change

## NAME and KILL

Testing the renaming (`NAME`, `TOBE`)function will be as follows:

- Request new connection with `DONE`, or navigate working directory into `root`
- Rename the existing file named `nameme.txt` into your desired name with following input command: `NAME nameme.txt` followed by `TOBE newname.txt`.
- Use `LIST V` to see the changed file name or check the root file manually

Test the deleting function (`KILL`) as follows:

- Navigate working directory into `root`
- Check if `killme.txt` exists using `LIST V` or check root file manually
- Delete the file named `killme.txt` with the following command: `KILL killme.txt`
- Use `LIST V` to see the file is deleted or check the root file manually

These commands work on any FILE within the root directory. Try with any files.

## RETR and STOR

To retrieve (`RETR`) files from the server `root` directories will be stored into the client side directories which can be found manually in the project folder called `clientRoot`.

- Navigate working directory into `root`
- Retrieve file from the server `root` into the `clientRoot` using the following command: `RETR test.txt`
  - *Server will reply size of file in bytes, server will poll for `SEND` or `STOP` commands*
- Input `SEND` and the file will be sent into `clientRoot`
- Check the file exists manually by looking in the project folder
- These steps can be repeated with any file again, but instead of `SEND` use `STOP` and the file will not be transferred.

With the `test.txt` file retrieved from the `root`, we can store by appending the contents which will test `STOR` and the `APP` mode

- Manually check the contents of the file of `test.txt` in both the `root` and `clientRoot`
- While in the same directory, input command: `STOR APP test.txt`, the send command for storage will be done automatically
- Using `LIST V` we can see that `test.txt` file has doubled in size and the contents have been duplicated

Testing the `OLD` mode of storing can be done as follows:

- Input `STOR OLD test.txt` 
- `test.txt` file should be reverted to the original retrieved file found in the `clientRoot`

Testing the `NEW` mode of storing can be done as follows:

- Input `KILL test.txt` to delete the file in the `root` directory
- Input `STOR NEW test.txt`



