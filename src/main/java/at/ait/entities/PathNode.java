package at.ait.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.graphdb.Node;

public class PathNode {
	Node node;
	PathNode predecessor;
	
 	public PathNode(Node node) {
		this.node = node;
		this.predecessor = null;
	}
 	
	public PathNode(Node node, PathNode path) {
		this.node = node;
		this.predecessor = path;
	}

	public List<Node> path() {
		List<Node> nodeList = new ArrayList<>();
		PathNode step = this;
		do {
			nodeList.add(step.node);
			step = step.predecessor;
		} while (step != null);
		return nodeList;
	}
	
	public static List<Node> constructPath(PathNode sourcePath, PathNode targetPath) {
		List<Node> nodeList = sourcePath.path();
		nodeList.remove(0);
		Collections.reverse(nodeList);
		nodeList.addAll(targetPath.path());
		return nodeList;
	}
}
