import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * PHP-based firewall module for filtering PHP file requests
 * Handles PHP file scanning, learning mode, and request validation
 */
public class FirewallPHP {

	// List of PHP paths with their parameter sets
	private static final List<PhpPath> phpPaths = new ArrayList<>();

	// Directory for scanning PHP files (from config)
	private static String phpDirectory = Configs.getParam("php_directory");

	// Path to XML file for saving/loading map
	private static String learningDataFile = Configs.getParam("phpLearningDataFile");

	// Host name from config
	private static String hostName = Configs.getParam("host");

	// Flag to track if initialization has been performed
	private static boolean initialized = false;

	/**
	 * Initializes PHP firewall
	 * Steps: 1) Check/create XML, 2) Load XML into structures, 3) Scan directory and update
	 */
	public static void initialize() {
		System.out.println("FIREWALL PHP: Initializing PHP firewall...");

		if (!Configs.getDefine("phpLearningDataFile")) {
			System.out.println("FIREWALL PHP: Warning: phpLearningDataFile not configured. PHP firewall will not use learning file.");
			return;
		}

		try {
			// Step 1: Check if XML exists, create if not
			ensureXmlFileExists();

			// Step 2: Load XML into data structures
			loadXML();

			// Step 3: Scan PHP directory and add new files
			scanPhpDirectory();

			// Update XML with any new files found
			updateXMLFile();

			initialized = true;
			System.out.println("FIREWALL PHP: Initialization completed. Loaded " + phpPaths.size() + " PHP paths.");

			// Add shutdown hook for saving learning data
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (Configs.getBoolean("phpLearning")) {
					saveAllowedRequests();
				}
			}));
		} catch (Exception e) {
			System.err.println("FIREWALL PHP: Error during initialization: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Ensures XML file exists, creates it if not
	 */
	private static void ensureXmlFileExists() throws Exception {
		File xmlFile = new File(learningDataFile);
		if (!xmlFile.exists()) {
			System.out.println("FIREWALL PHP: XML file does not exist, creating new one: " + learningDataFile);
			FileWriter fw = new FileWriter(xmlFile);
			fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			fw.write("<host name=\"" + escapeXml(hostName) + "\">\n");
			fw.write("</host>\n");
			fw.close();
		}
	}

	/**
	 * Escapes XML special characters
	 */
	private static String escapeXml(String str) {
		if (str == null) return "";
		return str.replace("&", "&amp;")
				  .replace("<", "&lt;")
				  .replace(">", "&gt;")
				  .replace("\"", "&quot;")
				  .replace("'", "&apos;");
	}

	/**
	 * Loads XML file into data structures
	 */
	private static void loadXML() throws Exception {
		File xmlFile = new File(learningDataFile);
		if (!xmlFile.exists()) {
			throw new Exception("XML file does not exist: " + learningDataFile);
		}

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(xmlFile);

		Element hostElement = doc.getDocumentElement();
		if (!hostElement.getTagName().equals("host")) {
			throw new Exception("Invalid XML structure: root element must be 'host'");
		}

		NodeList pathNodes = hostElement.getElementsByTagName("path");
		for (int i = 0; i < pathNodes.getLength(); i++) {
			Element pathElement = (Element) pathNodes.item(i);
			String pathName = pathElement.getAttribute("name");

			PhpPath phpPath = new PhpPath(pathName);

			// Load parameter sets
			Element paramsElement = (Element) pathElement.getElementsByTagName("params").item(0);
			if (paramsElement != null) {
				NodeList setNodes = paramsElement.getElementsByTagName("set");
				for (int j = 0; j < setNodes.getLength(); j++) {
					Element setElement = (Element) setNodes.item(j);
					String setContent = setElement.getTextContent().trim();

					Set<String> paramSet = new HashSet<>();
					if (!setContent.isEmpty()) {
						String[] params = setContent.split(",");
						for (String param : params) {
							paramSet.add(param.trim());
						}
					}

					phpPath.getParamSets().add(paramSet);
				}
			}

			phpPaths.add(phpPath);
		}

		System.out.println("FIREWALL PHP: Loaded " + phpPaths.size() + " paths from XML");
	}

	/**
	 * Scans PHP directory and adds new files to structures
	 */
	private static void scanPhpDirectory() throws Exception {
		if (phpDirectory == null || phpDirectory.trim().isEmpty()) {
			throw new Exception("PHP directory not configured");
		}

		Path dir = Paths.get(phpDirectory);
		if (!Files.exists(dir) || !Files.isDirectory(dir)) {
			throw new Exception("PHP directory does not exist: " + phpDirectory);
		}

		Set<String> foundFiles = new HashSet<>();
		
		Files.walk(dir)
			.filter(Files::isRegularFile)
			.filter(path -> path.toString().endsWith(".php"))
			.map(path -> path.toString().replaceFirst("^" + Pattern.quote(phpDirectory), ""))
			.forEach(path -> foundFiles.add(path));

		System.out.println("FIREWALL PHP: Found " + foundFiles.size() + " PHP files in directory");

		// Add new files to structures
		for (String fileName : foundFiles) {
			if (getPhpPathByName(fileName) == null) {
				PhpPath newPath = new PhpPath(fileName);
				phpPaths.add(newPath);
				System.out.println("FIREWALL PHP: Added new PHP file: " + fileName);
			}
		}

		// Check for files in structure that are not in directory
		for (PhpPath phpPath : phpPaths) {
			if (!foundFiles.contains(phpPath.getPathName())) {
				System.out.println("FIREWALL PHP: Warning - PHP file in structure but not in directory: " + phpPath.getPathName());
			}
		}
	}

	/**
	 * Finds PhpPath by file name
	 */
	private static PhpPath getPhpPathByName(String pathName) {
		for (PhpPath phpPath : phpPaths) {
			if (phpPath.getPathName().equals(pathName)) {
				return phpPath;
			}
		}
		return null;
	}

	/**
	 * Updates XML file with current data structures
	 */
	private static void updateXMLFile() throws Exception {
		if (learningDataFile == null) {
			return;
		}

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xmlContent.append("<host name=\"" + escapeXml(hostName) + "\">\n");

		for (PhpPath phpPath : phpPaths) {
			xmlContent.append(phpPath.toXmlString());
		}

		xmlContent.append("</host>\n");

		FileWriter fw = new FileWriter(learningDataFile);
		fw.write(xmlContent.toString());
		fw.close();

		System.out.println("FIREWALL PHP: XML file updated");
	}

	/**
	 * Saves learning data to XML file on shutdown
	 */
	private static void saveAllowedRequests() {
		try {
			updateXMLFile();
			System.out.println("FIREWALL PHP: Learning data saved on shutdown");
		} catch (Exception e) {
			System.err.println("FIREWALL PHP: Error saving learning data: " + e.getMessage());
		}
	}

	/**
	 * Extracts file name from path (e.g., "/path/to/file.php" -> "file.php")
	 */
	private static String extractFileName(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
/*
		// Check for directory traversal
		if (path.contains("../") || path.contains("..\\")) {
			return "";
		}

		// Extract file name
		int lastSlash = path.lastIndexOf("/");
		if (lastSlash >= 0) {
			return path.substring(lastSlash + 1);
		}
*/
		return path;
	}

	/**
	 * Checks if PHP file is known (without parameters)
	 * @param docName PHP document name/path
	 * @return true if file exists in structure, false otherwise
	 */
	public static boolean phpFirewall(String docName) {
		if (!Configs.getBoolean("FirewallRun")) {
			return true;
		}
/*
		if (!initialized) {
			return false;
		}
*/
		String fileName = extractFileName(docName);
		if (fileName.isEmpty()) {
			return false;
		}

		PhpPath phpPath = getPhpPathByName(fileName);
		return phpPath != null;
	}

	/**
	 * Main PHP firewall module: checks correctness of request to PHP file with parameters
	 * @param docName PHP document name/path
	 * @param param list of parameters
	 * @return true if request allowed, false otherwise
	 */
	public static boolean phpFirewall(String docName, List<String> param) {
		if (!Configs.getBoolean("FirewallRun")) {
			return true;
		}
/*
		if (!initialized) {
			return false;
		}
*/
		String fileName = extractFileName(docName);
		if (fileName.isEmpty()) {
			return false;
		}

		PhpPath phpPath = getPhpPathByName(fileName);
		if (phpPath == null) {
			return false;
		}

		// Learning mode
		if (Configs.getBoolean("phpLearning")) {
			if (phpPath.compareParams(param) == -1) {
				// New parameter set, add it
				phpPath.addParamSet(param);
				System.out.println("FIREWALL PHP: Learning - added new parameter set for " + fileName);
			}
			return true; // Always allow in learning mode
		
		} else {
			// Enforcement mode
			if (phpPath.compareParams(param) == -1) {
				System.out.println("FIREWALL PHP: Blocked unknown request to " + fileName + " with parameters: " + param);
				return false;
			}

			return true; // Allowed
		}
	}

	/**
	 * Forces directory rescan (useful for development/testing)
	 */
	public static void rescanPhpDirectory() {
		try {
			phpPaths.clear();
			initialized = false;
			initialize();
		} catch (Exception e) {
			System.err.println("FIREWALL PHP: Error during rescan: " + e.getMessage());
		}
	}

	/**
	 * @return number of known PHP files
	 */
	public static int getPhpFilesCount() {
		return phpPaths.size();
	}

	/**
	 * @return number of learned request patterns
	 */
	public static int getAllowedRequestsCount() {
		int count = 0;
		for (PhpPath phpPath : phpPaths) {
			count += phpPath.getParamSets().size();
		}
		return count;
	}
}
