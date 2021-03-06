package fr.inria.astor.core.setup;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.faultlocalization.IFaultLocalization;
import fr.inria.astor.core.faultlocalization.entity.SuspiciousCode;
import fr.inria.astor.util.FileUtil;

/**
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class ProjectRepairFacade {

	Logger logger = Logger.getLogger(Thread.currentThread().getName());

	protected ProjectConfiguration setUpProperties = new ProjectConfiguration();

	public ProjectRepairFacade(ProjectConfiguration properties) throws Exception {

		setProperties(properties);

	}

	/**
	 * Set up a project for a given mutator identifier.
	 * 
	 * @param currentMutatorIdentifier
	 * @throws IOException
	 */
	public synchronized void setupWorkingDirectories(String currentMutatorIdentifier) throws IOException {

		cleanMutationResultDirectories();

		copyOriginalCode(currentMutatorIdentifier);

		try {

			String originalAppBinDir = getProperties().getOriginalAppBinDir();

			copyOriginalBin(originalAppBinDir, currentMutatorIdentifier);// NEW
																			// ADDED
			copyOriginalBin(getProperties().getOriginalTestBinDir(), currentMutatorIdentifier);// NEW

		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
		copyData(currentMutatorIdentifier);

	}

	public void copyOriginalCode(String mutIdentifier) throws IOException {
			List<String>  dirs = getProperties().getOriginalDirSrc();
			System.out.println(dirs.toString());
			//The first element corresponds to application source
			String srcApp = dirs.get(0);
			//the second to the test folder
			String srctest = dirs.get(1);
			//we only want to generate the model of the application, so, we copy only it code, ignoring tests
			copyOriginalSourceCode(srcApp, mutIdentifier);
	}

	/**
	 * Copy the original code -from the path passed by parameter- to the
	 * mutation folder
	 * 
	 * @param pathOriginalCode
	 * @throws IOException
	 */
	public void copyOriginalSourceCode(String pathOriginalCode, String currentMutatorIdentifier) throws IOException {
		File destination = new File(getProperties().getWorkingDirForSource() + File.separator + currentMutatorIdentifier);
		destination.mkdirs();
		FileUtils.copyDirectory(new File(pathOriginalCode), destination);
	}

	/**
	 * Remove dir for a given mutation
	 * 
	 * @throws IOException
	 */
	public void cleanMutationResultDirectories(String currentMutatorIdentifier)
			throws IOException {

		removeDir(getProperties().getWorkingDirForSource() + File.separator + currentMutatorIdentifier);
		removeDir(getProperties().getWorkingDirForBytecode() + File.separator + currentMutatorIdentifier);
	}

	public void cleanMutationResultDirectories() throws IOException {

		removeDir(getProperties().getWorkingDirForSource());
		removeDir(getProperties().getWorkingDirForBytecode());
	}

	private void removeDir(String dir) throws IOException {
		File dirin = new File(dir);
		try {
			FileUtils.deleteDirectory(dirin);
		} catch (Exception ex) {
			logger.error("ex: " + ex.getMessage());
		}
		dirin.mkdir();
	}

	public boolean copyOriginalBin(String inDir, String mutatorIdentifier) throws IOException {
		boolean copied = false;
		if (inDir != null) {
			File original = new File(inDir);
			File dest = new File(getOutDirWithPrefix(mutatorIdentifier));
			dest.mkdirs();
			FileUtils.copyDirectory(original, dest);
			copied = true;
		}
		return copied;
	}

	public String getOutDirWithPrefix(String currentMutatorIdentifier) {
		return getProperties().getWorkingDirForBytecode() + File.separator + currentMutatorIdentifier;
	}

	public String getInDirWithPrefix(String currentMutatorIdentifier) {
		return getProperties().getWorkingDirForSource() + File.separator + currentMutatorIdentifier;
	}

	public void copyData(String currentMutatorIdentifier) throws IOException {
		
		String resourcesDir = getProperties().getDataFolder();
		if ( resourcesDir == null)
			return;
		
		String[] resources = resourcesDir.split(File.pathSeparator);
		File destFile = new File(getOutDirWithPrefix(currentMutatorIdentifier));
		
		for (String r : resources) {
			String path = ConfigurationProperties.getProperty("location");
			File source = new File(path + File.separator + r);
			if(!source.exists())
				return;
			//destFile.mkdirs();
			//for(File f:source.listFiles())
			FileUtils.copyDirectory(source, destFile);

		}
	
	}

	/**
	 * Return classpath form mutated variant.
	 * 
	 * @param currentMutatorIdentifier
	 * @return
	 * @throws MalformedURLException
	 */
	public URL[] getClassPathURLforProgramVariant(String currentMutatorIdentifier) throws MalformedURLException {

		List<URL> classpath = new ArrayList<URL>(getProperties().getDependencies());
		// bin
		URL urlBin = new File(getOutDirWithPrefix(currentMutatorIdentifier)).toURI().toURL();
		classpath.add(urlBin);

		URL[] cp = classpath.toArray(new URL[0]);
		return cp;
	}
	
	public List<SuspiciousCode> readSuspicious() throws Exception {
		List<SuspiciousCode> suspicious = new ArrayList<SuspiciousCode>();
		
		String loc = ConfigurationProperties.getProperty("location");
		String filename = loc;
		if (ConfigurationProperties.hasProperty("fixLocation")) {
			String fixLocation = ConfigurationProperties.getProperty("fixLocation");
			if (fixLocation.equals("fixingLocation.txt"))
				filename += File.separator + "fixingLocation.txt";
			else
				filename += File.separator + "fixLoc" + File.separator + fixLocation;
		} else {
			filename += File.separator + "fixingLocation.txt";
		}
		logger.info("FixingLocation used:\t" + filename);
		List<String> lines = FileUtil.fileToLines(filename);
		HashMap<Integer,Integer> key = new HashMap<Integer,Integer>();
		key.put(1, 1);
		for (String line : lines) {
			String[] split = line.split("\t");
			if (split.length != 4) {
				continue;
			}
			SuspiciousCode sl = new SuspiciousCode(split[0], split[1], Integer.parseInt(split[2]), Double.parseDouble(split[3]),key);
			suspicious.add(sl);
		}
		Collections.sort(suspicious, new ComparatorCandidates());
		return suspicious;
	}
	
	public HashMap<String, HashSet<Integer>> readFixLine() throws Exception {
		HashMap<String, HashSet<Integer>> fileFixLocs = new HashMap<String, HashSet<Integer>>();
		String loc = ConfigurationProperties.getProperty("location");
		String[] split = loc.split(File.separator);
		String filename = split[split.length - 1];
		String[] split2 = filename.split("_");
		filename = split2[0] + "_" + split2[1] + "_info.txt";
		String bugInfo = "";
		for (int i = 0; i < split.length - 1; i++)
			bugInfo += split[i] + File.separator;
		bugInfo += filename;
		
		List<String> lines = FileUtil.fileToLines(bugInfo);
		for (String line : lines) {
			if (line.startsWith("s")) {
				split = line.split("\t");
				String className = split[1];
				fileFixLocs.put(className, new HashSet<Integer>());
				for (int i = 2; i < split.length; i++)
					fileFixLocs.get(className).add(Integer.parseInt(split[i]));
			}
		}
		return fileFixLocs;
	}
	
	public List<SuspiciousCode> calculateSuspicious(IFaultLocalization faultLocalization) throws Exception {
		List<SuspiciousCode> candidates = this.calculateSuspicious(faultLocalization,
				ConfigurationProperties.getProperty("location") + File.separator
						+ ConfigurationProperties.getProperty("srcjavafolder"),
				getOutDirWithPrefix(ProgramVariant.DEFAULT_ORIGINAL_VARIANT),
				ConfigurationProperties.getProperty("packageToInstrument"), ProgramVariant.DEFAULT_ORIGINAL_VARIANT,
				getProperties().getFailingTestCases(), getProperties().getRegressionTestCases(),
				ConfigurationProperties.getPropertyBool("regressionforfaultlocalization"));
		return candidates;
	}

	public List<SuspiciousCode> calculateSuspicious(IFaultLocalization faultLocalization, String locationSrc,
			String locationBytecode, String packageToInst, String mutatorIdentifier, List<String> failingTest,
			List<String> allTest, boolean mustRunAllTest) throws Exception {

		if (faultLocalization == null)
			throw new IllegalArgumentException("Fault localization is null");

		List<String> testcasesToExecute = null;

		if (mustRunAllTest) {
			testcasesToExecute = allTest;
		} else {
			testcasesToExecute = failingTest;
		}

		if (testcasesToExecute == null || testcasesToExecute.isEmpty()) {
			new IllegalArgumentException("Astor needs at least one test case for running");
		}

		logger.info("-Executing Gzoltar classpath: " + locationBytecode + " from " + +testcasesToExecute.size()
				+ " classes with test cases");

		List<String> listTOInst = new ArrayList<String>();
		listTOInst.add(packageToInst);

		Set<String> classPath = new HashSet<String>();
		classPath.add(locationBytecode);
		for (URL dep : getProperties().getDependencies()) {
			classPath.add(dep.getPath());
		}

		List<SuspiciousCode> suspiciousStatemens = faultLocalization.searchSuspicious(locationBytecode,
				testcasesToExecute, listTOInst, classPath, locationSrc);

//		if (suspiciousStatemens == null || suspiciousStatemens.isEmpty())
//			throw new IllegalArgumentException("No suspicious gen for analyze");

		List<SuspiciousCode> filtercandidates = new ArrayList<SuspiciousCode>();

		for (SuspiciousCode suspiciousCode : suspiciousStatemens) {
			if (!suspiciousCode.getClassName().endsWith("Exception")) {
				filtercandidates.add(suspiciousCode);
			}
		}
		Collections.sort(filtercandidates, new ComparatorCandidates());
		return filtercandidates;
	}



	public ProjectConfiguration getProperties() {
		return setUpProperties;
	}

	public void setProperties(ProjectConfiguration properties) {
		this.setUpProperties = properties;
	}

	public class ComparatorCandidates implements Comparator<SuspiciousCode> {

		@Override
		public int compare(SuspiciousCode o1, SuspiciousCode o2) {
			if (o1 == null || o2 == null) {
				return 0;
			}
			return Double.compare(o2.getSuspiciousValue(), o1.getSuspiciousValue());
		}

	}
}
