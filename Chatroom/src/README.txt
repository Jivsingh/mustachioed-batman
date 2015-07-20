Messages between peers and the Server are sent using a Message Object defined in Message.java
It uses the following protocol to send messages between Server and Client:-
 * The Message object used for communication between Server and Client
 * 
 * Sending GET: target=ALL for getting all currently online users. GET:
 * target=<username> for getting that user's IP, port details.
 *
 * Sending BLOCK: value=0, target=<username> to unblock user with that name.
 * BLOCK: value=1, target=<username> to block user with that name.
 * 
 * Sending POST: sender=<username>, value=<password> to authenticate sender.
 * Sending POST: sender=<username>, value=<port number> to inform server of the
 * port a particular user would be listening on Also using POST from Server to
 * post requested user's address as a response to GET.
 * 
 * For Authentication:- Receiving ERROR means user was not authenticated, due to
 * reason in 'value'. OK means user was authenticated and 'value' contains
 * welcome message.
 
 Sample interaction between users foobar and columbia:-
 Connecting to IP: /192.168.0.8 and port: 6789
>Username: foobar
>Password: passpass
>Welcome to simple chat server!
Started server socket
User was authenticated
>Client listening on: 47417
message columbia kfh
>message columbia jhbvhj
>Your message could not be delivered as the recipient has blocked you
>

Connecting to IP: /192.168.0.8 and port: 6789
>Username: columbia
>Password: 116bway
>Welcome to simple chat server!
Started server socket
User was authenticated
>Client listening on: 47420
Still listening.... true
>foobar: kfh
block foobar
User foobar was successfully blocked
>

Commands are case sensitive and not much bounds checking is performed. So, "message columbia some-message" will be successful while just "message columbia" will fail. Commands to be used are 'exactly' according to the PA1 specification:-
message <user> <message>
block <user>
unblock <user>
logout
getaddress <user>
private <user> <message>

broadcast and online commands have not been implemented. Also offline messaging hasn't been implemented.

Usage is according to PA1 example for JAVA. Makefile is in the zip:-
Terminal 1
>make
>java Server 4009
Terminal 2
>make
>java Client 10.11.12.13 4009

Keep credentials.txt in the same folder as Server.java . It populates the list of valid username-passwords.