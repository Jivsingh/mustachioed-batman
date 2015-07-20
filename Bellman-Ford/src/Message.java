import java.io.Serializable;

/**
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
 * 
 */

public class Message implements Serializable {
	// Needed to ensure correct deserialization
	private static final long serialVersionUID = 434372575021768390L;

	// Message Types
	public enum Type {
		DATA, BROADCAST, LINKUP, LINKDOWN, ROUTE_UPDATE, CHANGECOST
	};

	private Type type;
	private Object value = null;

	public boolean keep_open = false;
	public String sender, target;

	Message(Type type, Object value) {
		this.type = type;
		this.value = value;
	}

	Message(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public Object getValue() {
		return value;
	}

}
