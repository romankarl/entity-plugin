package at.ait.entities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class Entity extends ServerPlugin {
	
	private static Logger logger = Logger.getLogger(Entity.class.getName());
	
	@Description("Create entities for all transactions of a new block.")
    @PluginTarget(Node.class)
    public String createEntities(@Source Node block) {
		GraphDatabaseService graphDb = block.getGraphDatabase();
		String height;
		try (Transaction tx = graphDb.beginTx()) {
			height = block.getProperty("height").toString();
			logger.fine("create entities for block with height " + height);
			for (Relationship contains : block.getRelationships(TGRelationshipType.CONTAINS))
				createEntity(contains.getEndNode());
			tx.success();
		}
		
		return "block with height " + height + " has been processed";
	}
	
	@Description("Create an entity for a new transaction.")
    @PluginTarget(Node.class)
    public Node createEntity(@Source Node transaction) {
		GraphDatabaseService graphDb = transaction.getGraphDatabase();
		
		Node entity = null;
		try (Transaction tx = graphDb.beginTx()) {
			logger.fine("create entity for transaction " + transaction.getProperty("txid"));
			Set<Node> inputAddresses = new HashSet<>();
			for (Relationship input : transaction.getRelationships(TGRelationshipType.INPUT)) {
				Node output = input.getStartNode();
				for (Relationship uses : output.getRelationships(TGRelationshipType.USES))
					inputAddresses.add(uses.getEndNode());
			}
			if (inputAddresses.size() >= 2) {
				Set<Node> entities = collectEntities(inputAddresses);
				Iterable<Node> addresses;
				if (entities.isEmpty()) {
					addresses = inputAddresses;
					entity = graphDb.createNode(TGLabel.Entity);
				} else if (entities.size() == 1) {
					addresses = inputAddresses;
					entity = entities.iterator().next();
				} else {
					entity = getLargestEntity(entities);
					entities.remove(entity);
					addresses = collectAddressesAndDeleteEntities(entities);
				}
				associateWith(addresses, entity);
			}
			tx.success();
		}
		return entity;
	}
	
	private Set<Node> collectEntities(Iterable<Node> addresses) {
		logger.fine("collect entities");
		Set<Node> entities = new HashSet<>();
		for (Node address : addresses) {
			Relationship belongsTo = address.getSingleRelationship(TGRelationshipType.BELONGS_TO, Direction.OUTGOING);
			if (belongsTo != null) {
				entities.add(belongsTo.getEndNode());
			}
		}
		return entities;
	}
	
	private Node getLargestEntity(Iterable<Node> entities) {
		Node largest_entity = null;
		int max_degree = 0;
		for (Node entity : entities) {
			int degree = entity.getDegree();
			if (degree > max_degree) {
				max_degree = degree;
				largest_entity = entity;
			}
		}
		return largest_entity;
	}
	
	private List<Node> collectAddressesAndDeleteEntities(Iterable<Node> entities) {
		logger.fine("collect addresses and delete obsolete entities");
		List<Node> addresses = new ArrayList<>();
		for (Node entity : entities) {
			for (Relationship belongsTo : entity.getRelationships()) {
				addresses.add(belongsTo.getStartNode());
				belongsTo.delete();
			}
			entity.delete();
		}
		return addresses;
	}
	
	private void associateWith(Iterable<Node> addresses, Node entity) {
		logger.fine("associate addresses with new entity");
		for (Node address : addresses)
			if (!address.hasRelationship(TGRelationshipType.BELONGS_TO))
				address.createRelationshipTo(entity, TGRelationshipType.BELONGS_TO);
	}
	
	@Description("Find a path between two nodes.")
	@PluginTarget(Node.class)
	public Path findPath(@Source Node source,
			@Description("The node to find the shortest path to.") @Parameter(name="target") Node target) {
		Path path = null;
		try (Transaction tx = source.getGraphDatabase().beginTx()) {
			for (Path p : source.getGraphDatabase().traversalDescription()
					.breadthFirst()
					.relationships(TGRelationshipType.USES)
					.relationships(TGRelationshipType.INPUT, Direction.OUTGOING)
					.relationships(TGRelationshipType.OUTPUT, Direction.OUTGOING)
					.traverse(source)) {
				Node currentNode = p.endNode();
				if (currentNode.equals(target)) {
					path = p;
					break;
				}
			}
			tx.success();
		}
		return path;
	}
	
	// not working and may be obsolete
	@Description("Find a path between two nodes. Should be fast.")
	@PluginTarget(Node.class)
	public Path findPathWithBidirectionalTraversal(@Source Node source,
			@Description("The node to find the shortest path to.") @Parameter(name="target") Node target) {
		Path path = null;
		try (Transaction tx = source.getGraphDatabase().beginTx()) {
			TraversalDescription startSideDescription = source.getGraphDatabase().traversalDescription()
					.breadthFirst()
					.relationships(TGRelationshipType.USES)
					.relationships(TGRelationshipType.INPUT, Direction.OUTGOING)
					.relationships(TGRelationshipType.OUTPUT, Direction.OUTGOING);
			for (Path p : source.getGraphDatabase().bidirectionalTraversalDescription()
					.mirroredSides(startSideDescription)
					.traverse(source, target)) {
				path = p;
				break;
			}
			tx.success();
		}
		return path;
	}
	
	@Description("Find a path between two nodes. Should be fast.")
	@PluginTarget(Node.class)
	public String findPathWithBidirectionalStrategy(@Source Node source,
			@Description("The node to find the shortest path to.") @Parameter(name="target") Node target) {
		String result;
		try (Transaction tx = source.getGraphDatabase().beginTx()) {
			PathSearch search = new PathSearch(source, target);
			List<Node> path = search.start();
			result = path != null ? toJson(path) : new JSONObject().put("error", "no path exists").toString();
			tx.success();
		}
		return result;
	}

	private String toJson(List<Node> path) {
		JSONArray pathJson = new JSONArray();
		for (Node output : path) {
			if (pathJson.length() != 0) {
				Node transaction = output.getSingleRelationship(TGRelationshipType.OUTPUT, Direction.INCOMING).getStartNode();
				pathJson.put(transaction.getAllProperties());
			}
			JSONObject outputJson = new JSONObject(output.getAllProperties());
			Iterable<Relationship> rels = output.getRelationships(TGRelationshipType.USES, Direction.OUTGOING);
			JSONArray addresses = new JSONArray();
			for (Relationship rel : rels) {
				addresses.put(rel.getEndNode().getProperty("address"));
			}
			outputJson.put("addresses", addresses);
			pathJson.put(outputJson);
		}
		return new JSONObject().put("path", pathJson).toString();
	}
}
