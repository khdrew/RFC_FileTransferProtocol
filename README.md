# COMPSYS 725 ASSIGNMENT 1

RFC913 - Server and Client

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

User data is stored as a `.json` file as a make shift database. User, accounts and passwords are as follows:


| User  | Account | Password |
| ----- | ------- | -------- |
| admin | N/A     | N/A      |
| u1    | a1      | p1       |
| u2    | N/A     | N/A      |
| N/A   | a3      | p3       |
| N/A   | a4      | N/A      |

Entries with N/A are entries that are not required and can bypass entries of values for their respective user/account.

Upon starting the server, the user data is loaded in and printed in the console.

## Testing USER, ACCT and PASS

With server and client open, enter into the client console the following:

`USER admin` or `USER u2`  - will log in the server without any account and password being specified.

Closing the client connection can be done with input of `DONE` in the client console. This will allow you to log in again. Run `ClientMain.java` again to request another connection to the server. Make sure closing the connection is done with this command, not with a force stop unless the server is restarted as well.

Input `USER u1`, `ACCT a1` and `PASS p1` to log into `u1/a1`. 

Reset the connection again with `DONE`  and input the following in any order `ACCT a3` and `PASS p3`, this will log into `a3`. This can be done again and log in with only just `ACCT a4`.



## Testing CDIR LIST and TYPE

Once logged in, the default directory will be `root`. The client user cannot back out of the `root` directory. 

Manually check the directory by checking the project file. The contents of the 