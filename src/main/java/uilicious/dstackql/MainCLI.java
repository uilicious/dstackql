package uilicious.dstackql;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picoded.core.file.ConfigFileSet;
import uilicious.dstackql.stack.MultiStackLoader;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "DStackQL", mixinStandardHelpOptions = true, version = "0.1.1", description = "DStackQL Server Program")
class MainCLI implements Callable<Integer> {
	
	// Example of arguments format
	// kept here for future refrence
	// See: https://picocli.info/
	//-----------------------------------------------------
	// @Parameters(index = "0", description = "The file whose checksum to calculate.")
	// private File file;	
	// @Option(names = { "-a", "--algorithm" }, description = "MD5, SHA-1, SHA-256, ...")
	// private String algorithm = "SHA-256";
	
	@Option(names = { "-c", "--config" }, description = "Config directory to load settings from")
	private File configDir = new File("./config");
	
	@Override
	public Integer call() throws Exception {
		
		// Check if the config directory is valid
		if (!configDir.isDirectory()) {
			throw new RuntimeException("Missing valid config directory : "
				+ configDir.getCanonicalPath());
		}
		
		// The starting console log
		System.out.println("## Starting DStackQL ...");

		// Lets load the config accordingly
		ConfigFileSet config = new ConfigFileSet( configDir );

		// Lead the multi stack
		MultiStackLoader multiStack = new MultiStackLoader( config.fetchGenericConvertStringMap("dstack") );

		// @TODO load the GraphQL server

		// Trigger the preloader (if enabled)
		if( config.fetchBoolean("sys.preloader.enable", true) == true ) {
			multiStack.runMultiThreadedPreloader( config.fetchInt("sys.preloader.threads", 6) );
		}
		
		// Post server sleep
		System.out.println("## Post setup cleanup (sleeping)");
		Thread.sleep(10000);

		// Application exit
		return 0;
	}
	
	// this example implements Callable, so parsing, error handling and handling user
	// requests for usage help or version help can be done with one line of code.
	public static void main(String... args) {
		int exitCode = new CommandLine(new MainCLI()).execute(args);
		System.exit(exitCode);
	}
}