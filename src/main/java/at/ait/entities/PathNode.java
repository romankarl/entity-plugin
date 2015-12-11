package at.ait.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.graphdb.Node;

public class PathNode {
	Node node;
	PathNode path;
	
 	public PathNode(Node node) {
		this.node = node;
		this.path = null;
	}
 	
	public PathNode(Node node, PathNode path) {
		this.node = node;
		this.path = path;
	}

	public List<Node> path() {
		List<Node> nodeList = new ArrayList<>();
		PathNode step = this;
		do {
			nodeList.add(step.node);
			step = step.path;
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
