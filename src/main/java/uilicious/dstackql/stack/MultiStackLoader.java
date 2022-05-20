package uilicious.dstackql.stack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import picoded.core.struct.GenericConvertHashMap;
import picoded.core.struct.GenericConvertMap;
import picoded.dstack.DStack;
import picoded.dstack.DataObject;
import picoded.dstack.DataObjectMap;

public class MultiStackLoader extends GenericConvertHashMap<String, DStack> {
	
	// Logger to use, for config file warnings
	// private static final Logger LOGGER = Logger.getLogger(MultiStackLoader.class.getName());
	
	// Config map
	protected GenericConvertMap<String, Object> stackConfigMap = null;
	
	/**
	 ** Initialized the multiple stacks, given the config
	 **
	 ** @param  inStackConfigMap configuration map, containing the various individual dstack config
	 */
	public MultiStackLoader(GenericConvertMap<String, Object> inStackConfigMap) {
		// set up the config
		stackConfigMap = inStackConfigMap;
		
		// Iterate each, and load each one of them
		for (String name : stackConfigMap.keySet()) {

			// Get the stack config, to validate if its "enabled"
			GenericConvertMap<String,Object> stackConfig = stackConfigMap.fetchGenericConvertStringMap(name + ".dstack");

			// Skip if not enabled
			if( stackConfig == null || stackConfig.getBoolean("enable", true) == false ) {
				continue;
			}

			// Setup the dstack object and save it
			this.put(name,new DStack(stackConfig));
		}
	}
	
	//---------------------------------------------------------------------
	//
	//  Preloader logic
	//
	//---------------------------------------------------------------------

	/**
	 ** Run the preloader, across multiple threads
	 ** 
	 ** @param threadCount maximum number of concurrent preloaders
	 */
	public void runMultiThreadedPreloader(int threadCount) {
		runMultiThreadedPreloader( this.keySet(), threadCount );
	}

	/**
	 ** Run the preloader, across multiple threads
	 ** 
	 ** @param stackNames  stack to scan against
	 ** @param threadCount maximum number of concurrent preloaders
	 */
	public void runMultiThreadedPreloader(Collection<String> stackNames, int threadCount) {

		// Event logging
		System.out.println("## [Preloader] Starting multi-threaded preloader with thread count : "+threadCount);

		// The executor service pool
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);

		// For each stack name, lets iterate and start submitting "preloader tasks"
		for(String stackName : stackNames) {

			// Lets fetch the individual preloader config
			GenericConvertMap<String,Object> preloaderConfig = stackConfigMap.fetchGenericConvertStringMap(stackName + ".preloader");

			// Skip various preloader stack that is not relevent
			if( preloaderConfig == null || preloaderConfig.getBoolean("enable", false) == false ) {
				continue;
			}

			// Get the DStack object
			DStack stackObj = this.get(stackName);

			// Lets load the various preloader types
			schedulePreloaderThreads(stackName, stackObj, pool, preloaderConfig, "DataObjectMap" );
		}

		// Lets wait for pool for completion
		pool.shutdown();
		try {
			if( pool.awaitTermination(30L, TimeUnit.DAYS) == false ) {
				pool.shutdownNow();
				throw new RuntimeException("## [Preloader] Terminating due to 30 days timeout !");
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	// Helper function for preloading individual threads
	protected static void schedulePreloaderThreads(String stackName, DStack stack, ExecutorService pool, GenericConvertMap<String,Object> preloaderConfig, String structType ) {
		// Get the preloader config map
		GenericConvertMap<String,Object> structConfig = preloaderConfig.getGenericConvertStringMap(structType, "{}");
		List<String> structNames = new ArrayList<String>(structConfig.keySet());
		Collections.shuffle( structNames );

		// Lets iterate the struct list, in random order, and schedule them
		for(String structName : structNames) {
			schedulePreloaderThreads(stackName, stack, pool, structType, structName);
		}
	}

	// Helper function for preloading individual threads
	protected static void schedulePreloaderThreads(String stackName, DStack stack, ExecutorService pool, String structType, String structName) {
		// The runnable task to setup
		Runnable runnableTask = buildTheRunnableTask(stackName, stack, structType, structName);
		if( runnableTask == null ) {
			return;
		}
		// Schedule the task
		pool.submit( runnableTask );
	}
	// Build the runnable task, which is then scheduled accordingly
	protected static Runnable buildTheRunnableTask(String stackName, DStack stack, String structType, String structName) {
		if( structType.equalsIgnoreCase("DataObjectMap") ) {
			return () -> {
				try {
					// Log the output
					System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Starting Preloading ...");
					long count = 0;

					// Get the DataObjectMap
					DataObjectMap dom = stack.getDataObjectMap(structName);

					// Lets iterate it 
					DataObject obj = dom.looselyIterateObject(null);
					while( obj != null ) {
						try {
							// Lets just load the keyset, to help ensure data is laoded
							obj.keySet();
							
							// lets go to the next object
							obj = dom.looselyIterateObject(obj);

							// Increment and log it
							count++;
							if( count % 100 == 1 ) {
								System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Preloading ("+count+"/"+dom.size()+") "+obj._oid());
							}

						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					// Finish the output
					System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Finished Preloading !!!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}
		return null;
	}

}