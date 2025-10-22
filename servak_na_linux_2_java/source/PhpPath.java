import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a PHP file path with its allowed parameter sets
 */
public class PhpPath {
	private String pathName;
	private List<Set<String>> paramSets; // List of parameter sets, each set contains parameter names

	public PhpPath(String pathName) {
		this.pathName = pathName;
		this.paramSets = new ArrayList<>();
	}

	/**
	 * Compares input parameters with existing parameter sets
	 * @param param list of parameter names
	 * @return set index (0-based) if match found, -1 if no match
	 */
	public int compareParams(List<String> param) {
		// Handle null or empty param as a special case
		Set<String> inputSet = new HashSet<>();
		if (param != null) {
			inputSet.addAll(param);
		}

		// Check each parameter set
		for (int i = 0; i < paramSets.size(); i++) {
			Set<String> storedSet = paramSets.get(i);
			// Check if all parameters in input are present in stored set
			if (storedSet.containsAll(inputSet) && inputSet.containsAll(storedSet)) {
				return i;
			}
			/*Set<String> storedSet = paramSets.get(i);

			System.out.println("compareParams(): comparing");
			System.out.println("  storedSet[" + i + "] = " + storedSet);
			System.out.println("  inputSet            = " + inputSet);

			if (storedSet.containsAll(inputSet) && inputSet.containsAll(storedSet)) {
				System.out.println("  -> MATCH found at index " + i);
				return i;
			} else {
				System.out.println("  -> no match\n");
			}*/
		}
		return -1;
	}

	/**
	 * Adds a new parameter set if it doesn't already exist
	 * @param param list of parameter names
	 * @return true if added, false if already exists
	 */
	public boolean addParamSet(List<String> param) {
		Set<String> newSet = new HashSet<>();
		if (param != null) {
			newSet.addAll(param);
		}

		// Check if this set already exists
		for (Set<String> existingSet : paramSets) {
			if (existingSet.containsAll(newSet) && newSet.containsAll(existingSet)) {
				return false; // Already exists
			}
		}

		paramSets.add(newSet);
		return true; // Added
	}

	/**
	 * Generates XML string representation of this path
	 * @return XML string for this path element
	 */
	public String toXmlString() {
		StringBuilder sb = new StringBuilder();
		sb.append("  <path name=\"").append(escapeXml(pathName)).append("\">\n");
		sb.append("    <params>\n");

		for (int i = 0; i < paramSets.size(); i++) {
			Set<String> set = paramSets.get(i);
			sb.append("      <set id=\"").append(i + 1).append("\">");
			
			if (!set.isEmpty()) {
				List<String> sortedParams = new ArrayList<>(set);
				java.util.Collections.sort(sortedParams);
				sb.append(String.join(",", sortedParams));
			}
			
			sb.append("</set>\n");
		}

		sb.append("    </params>\n");
		sb.append("  </path>\n");
		return sb.toString();
	}

	/**
	 * Escapes XML special characters
	 */
	private String escapeXml(String str) {
		return str.replace("&", "&amp;")
				  .replace("<", "&lt;")
				  .replace(">", "&gt;")
				  .replace("\"", "&quot;")
				  .replace("'", "&apos;");
	}

	public String getPathName() {
		return pathName;
	}

	public List<Set<String>> getParamSets() {
		return paramSets;
	}

	public void setParamSets(List<Set<String>> paramSets) {
		this.paramSets = paramSets;
	}

	public boolean hasParamSets() {
		return !paramSets.isEmpty();
	}
}
