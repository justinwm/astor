package fr.inria.astor.core.validation.validators;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.ProgramVariantValidationResult;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.util.Converters;

public class ProcessJUnitCoreValidator extends ProgramValidator {
	
	protected Logger log = Logger.getLogger(Thread.currentThread().getName());
	public String sampleTestId;

	public ProcessJUnitCoreValidator(String testId) {
		sampleTestId = testId;
	}
	
	@Override
	public ProgramVariantValidationResult validate(ProgramVariant variant, ProjectRepairFacade projectFacade) {
		// TODO Auto-generated method stub
		
		// Execute the regression
		try {
			URL[] bc = createClassPath(variant, projectFacade);
			
			log.info("-Running JUnitCore validation:\t" + sampleTestId);
			currentStats.numberOfFailingTestCaseExecution++;
			
			long t1 = System.currentTimeMillis();
			String jvmPath = ConfigurationProperties.getProperty("jvm4testexecution");
			String classpath = urlArrayToString(bc);
			
			Process p = null;
			jvmPath += File.separator + "java";
			String systemcp = 	defineInitialClasspath();
			classpath = systemcp + File.pathSeparator + classpath;
			
			List<String> command = new ArrayList<String>();
			command.add(jvmPath);
			command.add("-Xmx2048m");
			command.add("-cp");
			command.add(classpath);
			command.add("JUnitCore_" + sampleTestId);
			
			ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]));
			pb.redirectOutput();
			pb.redirectErrorStream(true);
			pb.directory(new File((ConfigurationProperties.getProperty("location"))));
			
			long t_start = System.currentTimeMillis();
			p = pb.start();
			p.waitFor(ConfigurationProperties.getPropertyInt("tmax2"),TimeUnit.MILLISECONDS);
			long t_end = System.currentTimeMillis();
			
			log.debug("Execution time " + ((t_end - t_start) / 1000) + " seconds");
			
			long t2 = System.currentTimeMillis();
			currentStats.time1Validation.add((t2 - t1));
			currentStats.passFailingval1++;
			
			TestResult trregression = getTestResult(p);
			p.destroy();
			if (trregression == null) {
				currentStats.unfinishValidation++;
				return null;
			} else {
				log.debug(trregression);
				currentStats.numberOfTestcasesExecutedval2 += trregression.casesExecuted;
				currentStats.numberOfRegressionTestCases = trregression.casesExecuted;
				return new TestCasesProgramValidationResult(trregression, trregression.wasSuccessful(),
						(trregression != null));
			}
			
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

	}

	protected String urlArrayToString(URL[] urls) {
		String s = "";
		for (int i = 0; i < urls.length; i++) {
			URL url = urls[i];
			s += url.getPath() + File.pathSeparator;
		}
		
		return s;
	}
	
	protected URL[] createClassPath(ProgramVariant mutatedVariant, ProjectRepairFacade projectFacade)
			throws MalformedURLException {
		String bytecodeOutput = projectFacade.getOutDirWithPrefix(mutatedVariant.currentMutatorIdentifier());
		File variantOutputFile = new File(bytecodeOutput);

		URL[] defaultSUTClasspath = projectFacade
				.getClassPathURLforProgramVariant(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);
		List<URL> originalURL = new ArrayList(Arrays.asList(defaultSUTClasspath));

		String classpath = System.getProperty("java.class.path");

		for (String path : classpath.split(File.pathSeparator)) {

			File f = new File(path);
			originalURL.add(new URL("file://" + f.getAbsolutePath()));

		}

		URL[] bc = null;
		if (mutatedVariant.getCompilation() != null) {
			MutationSupporter.currentSupporter.getSpoonClassCompiler().saveByteCode(mutatedVariant.getCompilation(),
					variantOutputFile);

			bc = Converters.redefineURL(variantOutputFile, originalURL.toArray(new URL[0]));
		} else {
			bc = originalURL.toArray(new URL[0]);
		}
		return bc;
	}
	
	protected TestResult getTestResult(Process p) {
		TestResult tr = new TestResult();
		boolean success = false;
		BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		try {
			int total = 0;
			int failing = 0;
			
			while ((line = in.readLine()) != null) {
				String[] split = line.split("\t");
				if (!(split.length == 2)) continue;
				if (split[1].equals("fail") || split.equals("pass")) total++;
				if (split[1].equals("fail")) {
					failing++;
					tr.failTest.add(split[0]);
				}
			}
			success = true;
			tr.casesExecuted = total;
			tr.failures = failing;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		if (success) {
			log.info("Successfully validating a patch");
			return tr;
		}
		else {
			log.error("Error reading the validation process\n");
			return null;
		}
		
	}
	
	public String defineInitialClasspath(){
		return (new File("./lib/jtestex-0.0.1.jar").getAbsolutePath());
	}
	
}