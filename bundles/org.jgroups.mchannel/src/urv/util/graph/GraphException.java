package urv.util.graph;

/**
 * This exception represents a failure in the graph
 * computation or behaviour.
 * 
 * @author Raul Gracia
 *
 */
public class GraphException extends Exception {

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;

	//	CONSTRUCTORS --
	
	public GraphException(String message) {
		super(message);
	}
}